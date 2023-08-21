package com.decathlon.github.kubernetesstatus.model.v1beta1;

import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectStatus;
import io.fabric8.kubernetes.model.annotation.PrinterColumn;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
@ApiModel(description = "GHD Deployment status. Used to prevent duplicate status on GH.")
public class GitHubDeploymentStatus {
    @PrinterColumn
    private String source;

    @PrinterColumn
    private long sourceGeneration;

    @PrinterColumn
    private KubeObjectStatus status;

    @PrinterColumn
    private String ref;

    @PrinterColumn
    private long deploymentId;
}
