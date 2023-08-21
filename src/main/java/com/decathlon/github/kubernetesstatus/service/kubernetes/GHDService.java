package com.decathlon.github.kubernetesstatus.service.kubernetes;

import com.decathlon.github.kubernetesstatus.model.GithubDeploymentRef;
import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeployment;
import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeploymentList;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class GHDService {
    private ApiClient apiClient;

    public List<GitHubDeployment> listDeployment(){
        var kubeApi=new GenericKubernetesApi<>(GitHubDeployment.class, GitHubDeploymentList.class, GithubDeploymentRef.GROUP, GithubDeploymentRef.CURRENT_VERSION, GithubDeploymentRef.PLURAL, apiClient);
        var list=kubeApi.list();
        if (list.isSuccess()){
            return list.getObject().getItems();
        }
        log.error("Error listing GHD: {} -- {}", list.getHttpStatusCode(), list.getStatus());
        return Collections.emptyList();
    }
}
