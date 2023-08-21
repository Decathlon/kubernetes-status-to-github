package com.decathlon.github.kubernetesstatus.service.kubernetes;

import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeploymentSpec;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class DynamicObjectExtractor {
    private ApiClient apiClient;

    private record GroupAndVersion(String group, String version) { }
    public DynamicKubernetesObject extractKubeObject(GitHubDeploymentSpec.NamespacedObject sourceRef) {
        var grpAndVersion=splitApiVersion(sourceRef.getApiVersion());
        String resourceName=sourceRef.getKind();

        if (!resourceName.endsWith("s")) {
            log.debug("SourceRef kind is not plural, adding 's' to it: {}s", resourceName);
            resourceName=resourceName+"s";
        }

        DynamicKubernetesApi dynamicApi = new DynamicKubernetesApi(grpAndVersion.group(), grpAndVersion.version(), resourceName.toLowerCase(), apiClient);

        var ns=sourceRef.getNamespace().isBlank() ? "default" : sourceRef.getNamespace();

        var k8sresult = dynamicApi.get(ns, sourceRef.getName());

        if (!k8sresult.isSuccess()) {
            log.warn("Failed to get k8s object {} in {}/{}: {}", sourceRef.getKind(), sourceRef.getNamespace(), sourceRef.getName(), k8sresult.getStatus().getMessage());
            return null;
        }

        return k8sresult.getObject();
    }

    private GroupAndVersion splitApiVersion(String apiVersion) {
        if (apiVersion.isBlank()) {
            return new GroupAndVersion("", "v1");
        }
        var apiGrpAndVersion=apiVersion.split("/");
        if (apiGrpAndVersion.length==1){
            return new GroupAndVersion("", apiGrpAndVersion[0]);
        }
        return new GroupAndVersion(apiGrpAndVersion[0], apiGrpAndVersion[1]);
    }
}
