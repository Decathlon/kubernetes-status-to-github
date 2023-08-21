package com.decathlon.github.kubernetesstatus.capture;

import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeployment;
import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeploymentStatus;
import com.decathlon.github.kubernetesstatus.service.CaptureService;
import com.decathlon.github.kubernetesstatus.service.UpdateService;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectResult;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectStatus;
import com.decathlon.github.kubernetesstatus.service.kubernetes.DynamicObjectExtractor;
import com.decathlon.github.kubernetesstatus.service.kubernetes.EventManager;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.Map;

class CaptureTests {

    String simpleGhd =
            """
                    apiVersion: github.decathlon.com/v1alpha1
                    kind: GitHubDeployment
                    metadata:
                      namespace: test
                      name: example
                    spec:
                      sourceRef:
                        apiVersion: apps/v1
                        kind: Deployment
                        name: test
                        namespace: test
                      extract:
                        containerName: test
                        regexp: (?<ref>.*)
                      repository:
                        name: test
                        environment: staging
                    """;

    String depOK = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              annotations:
                github.decathlon.com/test: testValue
              name: test
              generation: 2
              namespace: test
            spec:
              template:
                spec:
                  containers:
                    - name: test
                      image: test:1235
            status:
               observedGeneration: 2
               updatedReplicas: 1
               readyReplicas: 1
               availableReplicas: 1
               replicas: 1
               conditions:
                - type: Progressing
                  status: "True"
                  reason: NewReplicaSetAvailable
                - type: Available
                  status: "True"
            """;

    String depNoStatus = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
               annotations:
                 ref: 54321
               name: test
               generation: 1
            spec:
              template:
                spec:
                  containers:
                    - name: app
                      image: test:1234
            """;


    Yaml yaml = new Yaml();

    DynamicObjectExtractor doe;
    UpdateService us;
    EventManager em;
    CaptureService captureService;

    @BeforeEach
    void before() {
        doe = Mockito.mock(DynamicObjectExtractor.class);
        us = Mockito.mock(UpdateService.class);
        em = Mockito.mock(EventManager.class);

        captureService = new CaptureService(
                doe,
                us,
                em
        );
    }

    @Test
    void testNoChange() {
        var deployment = yaml.loadAs(simpleGhd, GitHubDeployment.class);
        captureService.capture(deployment);
        Mockito.verify(doe).extractKubeObject(deployment.getSpec().getSourceRef());
        Mockito.reset(doe);
        Mockito.verifyNoInteractions(us);
        Mockito.verifyNoInteractions(em);

        Mockito.when(doe.extractKubeObject(deployment.getSpec().getSourceRef())).thenReturn(Dynamics.newFromYaml(depNoStatus));

        captureService.capture(deployment);

        Mockito.verify(doe).extractKubeObject(deployment.getSpec().getSourceRef());
        Mockito.verifyNoInteractions(us);
        Mockito.verifyNoInteractions(em);
    }

    @Test
    void testChangeOnContainer() {
        var deployment = yaml.loadAs(simpleGhd, GitHubDeployment.class);
        deployment.getSpec().getExtract().setContainerName("app");
        deployment.getSpec().getExtract().setTemplate("{}");

        var dyn = Dynamics.newFromYaml(depNoStatus);
        Mockito.when(doe.extractKubeObject(deployment.getSpec().getSourceRef())).thenReturn(dyn);

        // Error in namespace/name
        captureService.capture(deployment);
        Mockito.verifyNoInteractions(us);
        Mockito.verifyNoInteractions(em);

        // namespace/name are correct
        dyn.getRaw().get("metadata").getAsJsonObject().addProperty("namespace", "test");
        captureService.capture(deployment);

        Mockito.verify(us).executeUpdate(deployment.getSpec().getRepository(), "1234", -1, new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, "replicas: 0/1"), Collections.emptyMap());

        var patch = GitHubDeployment.builder()
                .withStatus(GitHubDeploymentStatus.builder()
                        .withSource("Deployment/test/test")
                        .withSourceGeneration(1)
                        .withStatus(KubeObjectStatus.IN_PROGRESS)
                        .withRef("1234")
                        .withDeploymentId(0)
                        .build()
                ).build();

        Mockito.verify(em).addEvent(deployment, new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, "replicas: 0/1"), deployment.getSpec().getSourceRef(), patch);

        // Capture the ref from annotations
        deployment.getSpec().getExtract().setContainerName(null);
        deployment.getSpec().getExtract().setJsonPath("$.metadata.annotations.ref");
        deployment.getSpec().getExtract().setRegexp(null);
        deployment.getSpec().getExtract().setTemplate("release-");
        captureService.capture(deployment);
        Mockito.verify(us).executeUpdate(deployment.getSpec().getRepository(), "release-54321", -1, new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, "replicas: 0/1"), Collections.emptyMap());
    }

    @Test
    void testNoStatusOnGhd(){
        var deployment = yaml.loadAs(simpleGhd, GitHubDeployment.class);

        var dyn = Dynamics.newFromYaml(depOK);
        Mockito.when(doe.extractKubeObject(deployment.getSpec().getSourceRef())).thenReturn(dyn);

        captureService.capture(deployment);

        Mockito.verify(us).executeUpdate(deployment.getSpec().getRepository(), "1235", -1, new KubeObjectResult(KubeObjectStatus.CURRENT, "Deployment is available. Replicas: 1"), Map.of("test", "testValue"));
    }

    @Test
    void testStatusOnGhdWithoutStatus(){
        var deployment = yaml.loadAs(simpleGhd, GitHubDeployment.class);
        deployment.setStatus(new GitHubDeploymentStatus());

        var dyn = Dynamics.newFromYaml(depOK);
        Mockito.when(doe.extractKubeObject(deployment.getSpec().getSourceRef())).thenReturn(dyn);

        captureService.capture(deployment);

        Mockito.verify(us).executeUpdate(deployment.getSpec().getRepository(), "1235", -1, new KubeObjectResult(KubeObjectStatus.CURRENT, "Deployment is available. Replicas: 1"), Map.of("test", "testValue"));
    }

    @Test
    void testNormalChange(){
        var deployment = yaml.loadAs(simpleGhd, GitHubDeployment.class);
        deployment.setStatus(GitHubDeploymentStatus.builder()
                .withStatus(KubeObjectStatus.IN_PROGRESS)
                .withRef("1235")
                .withDeploymentId(5678)
                .withSourceGeneration(2)
                .build());

        var dyn = Dynamics.newFromYaml(depOK);
        Mockito.when(doe.extractKubeObject(deployment.getSpec().getSourceRef())).thenReturn(dyn);

        captureService.capture(deployment);
        Mockito.verify(us).executeUpdate(deployment.getSpec().getRepository(), "1235", 5678, new KubeObjectResult(KubeObjectStatus.CURRENT, "Deployment is available. Replicas: 1"), Map.of("test", "testValue"));
    }

    @Test
    void testFinalStatus(){
        var deployment = yaml.loadAs(simpleGhd, GitHubDeployment.class);
        deployment.setStatus(GitHubDeploymentStatus.builder()
                .withStatus(KubeObjectStatus.CURRENT)
                .withRef("1235")
                .withDeploymentId(5678)
                .withSourceGeneration(2)
                .build());

        var dyn = Dynamics.newFromYaml(depOK);
        Mockito.when(doe.extractKubeObject(deployment.getSpec().getSourceRef())).thenReturn(dyn);

        captureService.capture(deployment);
        Mockito.verifyNoInteractions(us);
    }


    @Test
    void noChangeIfBackward(){
        // If we have a deployment with a status final and the same ref We cannot upgrade.
        var deployment = yaml.loadAs(simpleGhd, GitHubDeployment.class);
        deployment.setStatus(GitHubDeploymentStatus.builder()
                .withStatus(KubeObjectStatus.CURRENT)
                .withRef("1235")
                .withDeploymentId(5678)
                .withSourceGeneration(2)
                .build());

        var dyn = Dynamics.newFromYaml(depOK);
        dyn.getRaw().get("status").getAsJsonObject().addProperty("replicas", 0);
        Mockito.when(doe.extractKubeObject(deployment.getSpec().getSourceRef())).thenReturn(dyn);

        captureService.capture(deployment);

        Mockito.verifyNoInteractions(us);
    }

    @Test
    void changeIfRefChangeEvenIfBackward(){
        // If we have a deployment with a status final and the same ref We cannot upgrade.
        var deployment = yaml.loadAs(simpleGhd, GitHubDeployment.class);
        deployment.setStatus(GitHubDeploymentStatus.builder()
                .withStatus(KubeObjectStatus.CURRENT)
                .withRef("1236")
                .withDeploymentId(5678)
                .withSourceGeneration(2)
                .build());

        var dyn = Dynamics.newFromYaml(depOK);
        dyn.getRaw().get("status").getAsJsonObject().addProperty("replicas", 0);
        Mockito.when(doe.extractKubeObject(deployment.getSpec().getSourceRef())).thenReturn(dyn);

        captureService.capture(deployment);

        Mockito.verify(us).executeUpdate(deployment.getSpec().getRepository(), "1235", -1, new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, "replicas: 0/1"), Map.of("test", "testValue"));
    }

    @Test
    void changeIfRefChange(){
        // If we have a deployment with a status final and the same ref We cannot upgrade.
        var deployment = yaml.loadAs(simpleGhd, GitHubDeployment.class);
        deployment.setStatus(GitHubDeploymentStatus.builder()
                .withStatus(KubeObjectStatus.CURRENT)
                .withRef("1236")
                .withDeploymentId(5678)
                .withSourceGeneration(2)
                .build());

        var dyn = Dynamics.newFromYaml(depOK);
        Mockito.when(doe.extractKubeObject(deployment.getSpec().getSourceRef())).thenReturn(dyn);

        captureService.capture(deployment);

        Mockito.verify(us).executeUpdate(deployment.getSpec().getRepository(), "1235", -1, new KubeObjectResult(KubeObjectStatus.CURRENT, "Deployment is available. Replicas: 1"), Map.of("test", "testValue"));
    }
}
