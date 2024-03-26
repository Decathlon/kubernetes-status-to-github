package com.decathlon.github.kubernetesstatus.service.kubernetes;

import com.decathlon.github.kubernetesstatus.model.GithubDeploymentRef;
import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeployment;
import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeploymentSpec;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectResult;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.extended.event.EventType;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.PatchUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class EventManager {
    private ApiClient apiClient;
    private EventRecorder eventRecorder;

    public void addEvent(GitHubDeployment deployment, KubeObjectResult status, GitHubDeploymentSpec.NamespacedObject sourceRef, GitHubDeployment patch) {
        try {
            Call fn = new CustomObjectsApi(apiClient).patchNamespacedCustomObjectStatus(
                    GithubDeploymentRef.GROUP,
                    GithubDeploymentRef.CURRENT_VERSION,
                    deployment.getMetadata().getNamespace(),
                    GithubDeploymentRef.PLURAL,
                    deployment.getMetadata().getName(),
                    patch
            ).buildCall(null);
            
            PatchUtils.<GitHubDeployment>patch(GitHubDeployment.class, () -> fn, V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH, apiClient);

            eventRecorder.event(
                    deployment,
                    EventType.Normal,
                    "UpdatedStatus",
                    "Successfully updated %s %s/%s status to %s",
                    sourceRef.getKind(),
                    sourceRef.getNamespace(), sourceRef.getName(),
                    status.status().pascalCase()
            );

        } catch (ApiException e) {
            log.error("Error updating the status for github deployment {}/{}", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName(), e);
        }
    }

}
