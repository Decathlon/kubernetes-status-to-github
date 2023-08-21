package com.decathlon.github.kubernetesstatus.service.transfer;

import com.decathlon.github.kubernetesstatus.data.io.TransferData;
import com.decathlon.github.kubernetesstatus.data.io.TransferResponse;
import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeploymentSpec;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@AllArgsConstructor
@Slf4j
public class TransferService {
    private WebClient webClient;
    public long executeUpdate(GitHubDeploymentSpec.RepositoryDetail repo, String ref, long env, KubeObjectResult status, Map<String, String> payload) {
        var transferData=new TransferData(repo, ref, env, status, payload);

        try {
            var resp = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Mono.just(transferData), TransferData.class)
                    .retrieve().toEntity(TransferResponse.class).block();

            assert resp != null;
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.error("Transfer failed: {}", resp.getStatusCode());
                return -1;
            }

            var body = resp.getBody();
            assert body != null;
            return body.eventId();
        }catch(WebClientResponseException ex){
            log.error("Transfer failed: {} with body {}", ex.getMessage(), ex.getResponseBodyAsString());
            return -1;
        }
    }
}
