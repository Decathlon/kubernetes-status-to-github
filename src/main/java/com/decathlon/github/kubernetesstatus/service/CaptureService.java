package com.decathlon.github.kubernetesstatus.service;

import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeployment;
import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeploymentSpec;
import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeploymentStatus;
import com.decathlon.github.kubernetesstatus.service.kstatus.Status;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectResult;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectStatus;
import com.decathlon.github.kubernetesstatus.service.kubernetes.DynamicObjectExtractor;
import com.decathlon.github.kubernetesstatus.service.kubernetes.EventManager;
import com.google.gson.JsonArray;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
@AllArgsConstructor
@Slf4j
public class CaptureService {
    private static final String ANNOTATION_PREFIX="github.decathlon.com/";

    private DynamicObjectExtractor dynamicObjectExtractor;
    private UpdateService updateService;
    private EventManager eventManager;


    public void capture(GitHubDeployment deployment) {
        List<DynamicKubernetesObject> dynamicKubernetesObjects = new ArrayList<>();
        KubeObjectResult status = null;


        log.debug("[{}/{}] Observing GitHub Deployment", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName());

        dynamicKubernetesObjects.add(dynamicObjectExtractor.extractKubeObject(deployment.getSpec().getSourceRef()));
        if (dynamicKubernetesObjects.isEmpty() || dynamicKubernetesObjects.get(0) ==null) {
            return;
        }

        for(GitHubDeploymentSpec.NamespacedObject namespacedObject : deployment.getSpec().getAdditionnalRef()) {
            dynamicKubernetesObjects.add(dynamicObjectExtractor.extractKubeObject(namespacedObject));
        }

        for(DynamicKubernetesObject dynamicKubernetesObject : dynamicKubernetesObjects) {
            log.info("[{}/{}] Will check {}/{} kubernetes object", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName(), dynamicKubernetesObject.getMetadata().getNamespace(), dynamicKubernetesObject.getMetadata().getName());
            boolean exit = false;
            KubeObjectResult statusTemp = Status.compute(dynamicKubernetesObject);
            switch (statusTemp.status()) {
                case FAILED:
                    exit = true;
                    status = statusTemp;
                    break;
                case CURRENT:
                    if(status.status().compareTo(KubeObjectStatus.CURRENT) == 0) {
                        status = statusTemp;
                    }
                default:
                    status = statusTemp;
                    break;
            }
            if(exit) break;
        }


        var ref=extractRef(deployment, dynamicKubernetesObjects.get(0));
        if (ref==null){
            log.warn("[{}/{}] Cannot extract ref from kube object with extract rule {}", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName(), deployment.getSpec().getExtract());
            return;
        }

        log.info("[{}/{}] ref found is {}, computed status is {}", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName(), ref, status);

        updateGhd(deployment, dynamicKubernetesObjects.get(0).getMetadata(), status, ref);
    }

    private void updateGhd(GitHubDeployment deployment, V1ObjectMeta metadata, KubeObjectResult status, String ref) {
        long generation=Objects.requireNonNullElse(metadata.getGeneration(), 0L);

        // Assert github deployment is targeting the correct source
        if ( !deployment.getSpec().getSourceRef().getName().equals(metadata.getName())
                || !deployment.getSpec().getSourceRef().getNamespace().equals(metadata.getNamespace())){
            return;
        }

        var currentStatus = deployment.getStatus();
        if (currentStatus != null &&
                currentStatus.getStatus()!=null &&
                !currentStatus.getStatus().canMoveTo(status.status()) &&
                ref.equals(currentStatus.getRef())
        ) {
            // already up to date, so nothing to do.
            // Or already end up to a final state, so nothing to do.
            log.info("[{}/{}] up to date", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName());
            return;
        }

        var payload=new HashMap<String, String>();
        var annotations = metadata.getAnnotations()!=null ? metadata.getAnnotations() : Collections.<String, String>emptyMap();
        annotations.entrySet().stream()
                .filter(es -> es.getKey().startsWith(ANNOTATION_PREFIX))
                .forEach(es -> payload.put(es.getKey().substring(ANNOTATION_PREFIX.length()), es.getValue()));

        var repo = deployment.getSpec().getRepository();

        var env = (currentStatus != null && ref.equals(currentStatus.getRef())) ? currentStatus.getDeploymentId() : -1;

        env = updateService.executeUpdate(repo, ref, env, status, payload);

        if (env == -1) {
            log.error("[{}/{}] Failed to update deployment", metadata.getNamespace(), metadata.getName());
            return;
        }

        var sourceRef=deployment.getSpec().getSourceRef();

        log.info("[{}/{}] New status has been push", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName());
        var patch = GitHubDeployment.builder().withStatus(
                GitHubDeploymentStatus.builder()
                        .withStatus(status.status())
                        .withRef(ref)
                        .withDeploymentId(env)
                        .withSource(String.format("%s/%s/%s", sourceRef.getKind(), sourceRef.getNamespace(), sourceRef.getName()))
                        .withSourceGeneration(generation)
                        .build()
        ).build();

        eventManager.addEvent(deployment, status, sourceRef, patch);
    }

    private String extractRef(GitHubDeployment deployment, DynamicKubernetesObject kobj) {
        var extract=deployment.getSpec().getExtract();
        var rawValue=extractGlobalRef(deployment, kobj);
        if (rawValue==null) {
            return null;
        }

        String ref=extractSimpleRef(deployment, rawValue);
        if (ref==null || Strings.isBlank(extract.getTemplate())) {
            return ref;
        }

        if (extract.getTemplate().contains("{}")) {
            return extract.getTemplate().replace("{}", ref);
        }else{
            log.info("[{}/{}] The template {} does not contain {}, using it like a prefix", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName(), extract.getTemplate(), "{}");
            return extract.getTemplate()+ref;
        }
    }

    private String extractSimpleRef(GitHubDeployment deployment, String rawValue) {
        var extract=deployment.getSpec().getExtract();
        if (Strings.isBlank(extract.getRegexp())){
            return rawValue;
        }

        var pattern = Pattern.compile(extract.getRegexp());
        var matcher = pattern.matcher(rawValue);

        if (!matcher.find()) {
            log.warn("[{}/{}] Cannot extract ref from content '{}'. This does not match regexp '{}'", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName(), rawValue, extract.getRegexp());
            return null;
        }
        // Single group => ok return it
        if (matcher.groupCount()==1){
            return matcher.group(1);
        }
        // Multiple groups => return the 'ref' one if it is exist.
        try {
            return matcher.group("ref");
        }catch(IllegalArgumentException e){
            log.warn("[{}/{}] No ref captured with content '{}' and regexp '{}'. There may be multiple capture group but none named 'ref'.", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName(), rawValue, extract.getRegexp());
        }
        return null;
    }

    private String extractGlobalRef(GitHubDeployment deployment, DynamicKubernetesObject kobj) {
        var extract=deployment.getSpec().getExtract();
        var path=extract.getJsonPath();
        // If container is set, trying to extract data from spec->template->spec->containers.
        if (!Strings.isBlank(extract.getContainerName())) {
            path=switch(deployment.getSpec().getSourceRef().getKind()){
                case "Deployment", "ReplicaSet", "Job", "StatefulSet", "DaemonSet" ->String.format("$.spec.template.spec.containers[?(@.name == '%s')].image", extract.getContainerName().replace("'", ""));
                case "Pod"->String.format("$.spec.containers[?(@.name == '%s')].image", extract.getContainerName().replace("'", ""));
                case "CronJob"->String.format("$.spec.jobTemplate.spec.template.spec.containers[?(@.name == '%s')].image", extract.getContainerName().replace("'", ""));
                default -> {
                    log.warn("[{}/{}] The extract rule specify a container name but the source kind is not supported", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName());
                    yield null;
                }
            };
        }
        if (Strings.isBlank(path)) {
            log.warn("[{}/{}] The extract rule do not specify any container nor jsonPath", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName());
            return null;
        }

        Configuration conf = Configuration.builder().jsonProvider(new GsonJsonProvider())
                .options(Option.ALWAYS_RETURN_LIST, Option.SUPPRESS_EXCEPTIONS).build();
        JsonArray objArrJ = JsonPath.using(conf).parse(kobj.getRaw()).read(path);

        if (objArrJ.size() != 1) {
            log.warn("[{}/{}] JsonPath {} not found in the targeted k8s object", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName(), path);
            return null;
        }
        if (!objArrJ.get(0).isJsonPrimitive()) {
            log.warn("[{}/{}] JsonPath {} retrieve a value, but it is a complex object (not a string)", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName(), path);
            return null;
        }

        String val=objArrJ.get(0).getAsString();

        if (!Strings.isBlank(extract.getContainerName())) {
            var s=val.split(":");
            if (s.length==2) {
                val=s[1];
            }else{
                val="latest";
            }
        }
        return val;
    }
}
