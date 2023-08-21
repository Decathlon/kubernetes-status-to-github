package com.decathlon.github.kubernetesstatus.data.io;

import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeploymentSpec;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectResult;

import java.util.Map;

public record TransferData(GitHubDeploymentSpec.RepositoryDetail repo, String ref, long env, KubeObjectResult status, Map<String, String> payload) {
}
