package com.decathlon.github.kubernetesstatus.transfer;

import com.decathlon.github.kubernetesstatus.mock.MockServer;
import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeploymentSpec;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectResult;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectStatus;
import com.decathlon.github.kubernetesstatus.service.transfer.TransferService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TransferTest {
    @BeforeAll
    public static void init() {
        MockServer.creators();
    }

    @AfterAll
    public static void destroyers() {
        MockServer.destroyers();
    }

    WebClient webClient=WebClient.builder()
            .baseUrl("http://localhost:9999/transfer")
            .build();

    @Test
    void transferOkTest(){
        TransferService t=new TransferService(webClient);

        var repo=new GitHubDeploymentSpec.RepositoryDetail();
        var ref="ok";
        long env=1;
        KubeObjectResult status=new KubeObjectResult(KubeObjectStatus.CURRENT, "ok");
        Map<String, String> payload=Map.of("key","value");

        var res=t.executeUpdate(repo, ref, env, status, payload);
        assertThat(res).isEqualTo(1234);
    }

    @Test
    void transferKoTest(){
        TransferService t=new TransferService(webClient);

        var repo=new GitHubDeploymentSpec.RepositoryDetail();
        var ref="ko";
        long env=1;
        KubeObjectResult status=new KubeObjectResult(KubeObjectStatus.CURRENT, "ok");
        Map<String, String> payload=Map.of("key","value");

        var res=t.executeUpdate(repo, ref, env, status, payload);
        assertThat(res).isEqualTo(-1);
    }

}
