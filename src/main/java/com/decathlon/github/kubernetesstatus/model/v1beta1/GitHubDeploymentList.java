package com.decathlon.github.kubernetesstatus.model.v1beta1;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

@Data
@Builder
@ApiModel(description = "GitHubDeployment object list.")
public class GitHubDeploymentList implements KubernetesListObject {
    @Builder.Default
    private String apiVersion="v1";

    @Builder.Default
    private String kind="List";

    @Singular
    private List<GitHubDeployment> items;

    private V1ListMeta metadata;
}
