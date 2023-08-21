package com.decathlon.github.kubernetesstatus.service;

import com.decathlon.github.kubernetesstatus.data.properties.AppMode;
import com.decathlon.github.kubernetesstatus.data.properties.AppProperties;
import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeploymentSpec;
import com.decathlon.github.kubernetesstatus.service.github.GitHubEnvironmentService;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectResult;
import com.decathlon.github.kubernetesstatus.service.transfer.TransferService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@AllArgsConstructor
public class UpdateService {
    private AppProperties appProperties;
    private GitHubEnvironmentService gitHubEnvironmentService;
    private TransferService transferService;

    public long executeUpdate(GitHubDeploymentSpec.RepositoryDetail repo, String ref, long env, KubeObjectResult status, Map<String, String> payload){
        // Native Conditional is not resolved at runtime wihout big config change.
        if (appProperties.getMode()== AppMode.CAPTURE_AND_TRANSFER) {
            return transferService.executeUpdate(repo, ref, env, status, payload);
        }else{
            return gitHubEnvironmentService.executeUpdate(repo, ref, env, status, payload);
        }
    }
}
