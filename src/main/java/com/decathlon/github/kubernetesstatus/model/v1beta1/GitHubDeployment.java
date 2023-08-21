package com.decathlon.github.kubernetesstatus.model.v1beta1;

import com.decathlon.github.kubernetesstatus.model.GithubDeploymentRef;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
@ApiModel(description = "Main GitHubDeployment object.")
public class GitHubDeployment implements io.kubernetes.client.common.KubernetesObject{
    @Builder.Default
    private String apiVersion = GithubDeploymentRef.GROUP+"/v1beta1";

    @Builder.Default
    private String kind = GithubDeploymentRef.KIND;

    //Refer to the Kubernetes API documentation for the fields of the metadata field.
    private V1ObjectMeta metadata;

    //spec
    private GitHubDeploymentSpec spec;

    //status
    private GitHubDeploymentStatus status;
}
