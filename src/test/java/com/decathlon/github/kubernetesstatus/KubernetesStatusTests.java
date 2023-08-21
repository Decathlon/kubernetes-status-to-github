package com.decathlon.github.kubernetesstatus;

import com.decathlon.github.kubernetesstatus.configuration.transfer.TransferConfiguration;
import com.decathlon.github.kubernetesstatus.data.properties.AppProperties;
import com.decathlon.github.kubernetesstatus.mock.MockServer;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import static com.decathlon.github.kubernetesstatus.mock.OauthTools.CLIENT_CREDENTIAL;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KubernetesStatusTests {
    @Autowired
    TestRestTemplate rtTest;

    @Autowired
    OAuth2ClientProperties oAuth2ClientProperties;
    @Autowired
    AppProperties appProperties;

    @BeforeAll
    public static void init() {
        MockServer.creators();
    }

    @AfterAll
    public static void destroyers() {
        MockServer.destroyers();
    }

    @Test
    void contextLoads() {
        assertThat(oAuth2ClientProperties).isNotNull();
    }

    @Test
    void testFedClient() {
        TransferConfiguration config = new TransferConfiguration();
        var webClient = config.webClientTransfer(oAuth2ClientProperties, appProperties, WebClient.builder());

        var resp = webClient.get().uri("http://localhost:9999/test").retrieve();
        var body = resp.bodyToMono(String.class).block();
        assertThat(body).isEqualTo("abxd");
    }

    @Test
    void noTransfer() {
        var transferUrl = "/transfer";

        assertThat(rtTest.postForEntity(transferUrl, "{}", String.class).getStatusCode().value()).isEqualTo(401);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer a" + CLIENT_CREDENTIAL);
        var ret = rtTest.exchange(transferUrl, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);

        assertThat(ret.getStatusCode().value()).isEqualTo(401);

        headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + CLIENT_CREDENTIAL);
        ret = rtTest.exchange(transferUrl, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);

        assertThat(ret.getStatusCode().value()).isEqualTo(415);

        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer " + CLIENT_CREDENTIAL);
        ret = rtTest.exchange(transferUrl, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);

        assertThat(ret.getStatusCode().value()).isEqualTo(406);
    }

    @Test
    void simpleTransfer() {
        var transferUrl = "/transfer";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer " + CLIENT_CREDENTIAL);

        String req = """
                {
                  "repo":{
                    "name": "my-repo",
                    "environment": "my-env"
                  },
                  "ref": "1245",
                  "env": -1,
                  "status": {
                    "status": "CURRENT",
                    "message": "OK"
                  },
                  "payload": {
                    "param": "value",
                    "url": "http://here"
                  }
                }
                """;

        var ret = rtTest.exchange(transferUrl, HttpMethod.POST, new HttpEntity<>(req, headers), JsonNode.class);

        assertThat(ret.getStatusCode().value()).isEqualTo(200);
        var body = ret.getBody();
        assertThat(body).isNotNull();
        assertThat(body.has("eventId")).isTrue();
        assertThat(body.get("eventId").asLong()).isEqualTo(5);
    }

    @Test
    void newDeploymentTransfer() {
        var transferUrl = "/transfer";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer " + CLIENT_CREDENTIAL);

        String req = """
                {
                  "repo":{
                    "name": "my-repo",
                    "environment": "my-env2"
                  },
                  "ref": "1246",
                  "env": -1,
                  "status": {
                    "status": "IN_PROGRESS",
                    "message": "OK"
                  },
                  "payload": {
                    "param": "value",
                    "url": "http://here"
                  }
                }
                """;

        var ret = rtTest.exchange(transferUrl, HttpMethod.POST, new HttpEntity<>(req, headers), JsonNode.class);

        assertThat(ret.getStatusCode().value()).isEqualTo(200);
        var body = ret.getBody();
        assertThat(body).isNotNull();
        assertThat(body.has("eventId")).isTrue();
        assertThat(body.get("eventId").asLong()).isEqualTo(6);
    }

    @Test
    void transfertError422() {
        var transferUrl = "/transfer";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer " + CLIENT_CREDENTIAL);

        String req = """
                {
                  "repo":{
                    "name": "my-repo",
                    "environment": "my-env2"
                  },
                  "ref": "422",
                  "env": -1,
                  "status": {
                    "status": "IN_PROGRESS",
                    "message": "OK"
                  },
                  "payload": {
                    "param": "value",
                    "url": "http://here"
                  }
                }
                """;

        var ret = rtTest.exchange(transferUrl, HttpMethod.POST, new HttpEntity<>(req, headers), JsonNode.class);

        assertThat(ret.getStatusCode().value()).isEqualTo(422);
        var body = ret.getBody();
        assertThat(body).isNotNull();
        assertThat(body.has("message")).isTrue();
        assertThat(body.get("message").asText()).isEqualTo("From GitHub: reference does not exist");

        // Testing cache
        ret = rtTest.exchange(transferUrl, HttpMethod.POST, new HttpEntity<>(req, headers), JsonNode.class);

        assertThat(ret.getStatusCode().value()).isEqualTo(422);
        body = ret.getBody();
        assertThat(body).isNotNull();
        assertThat(body.has("message")).isTrue();
        assertThat(body.get("message").asText()).isEqualTo("An error 422 was returned by GitHub on this exact same request. We will not try again until a small duration.");

    }

}
