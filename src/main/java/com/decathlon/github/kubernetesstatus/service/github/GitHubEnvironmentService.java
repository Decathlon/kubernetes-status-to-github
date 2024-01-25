package com.decathlon.github.kubernetesstatus.service.github;

import com.decathlon.github.kubernetesstatus.data.properties.AppProperties;
import com.decathlon.github.kubernetesstatus.model.v1beta1.GitHubDeploymentSpec;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectResult;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Map;

@Service
@AllArgsConstructor
@Slf4j
public class GitHubEnvironmentService {
    private static final String ENV_URL_SUFFIX = "url";
    private static final LinkedList<String> cachedError = new LinkedList<>();

    private ObjectMapper mapper;
    private RestTemplate restTemplate;
    private AppProperties appProperties;

    public long executeUpdate(GitHubDeploymentSpec.RepositoryDetail repo, String ref, long env, KubeObjectResult status, Map<String, String> payload) {
        try {
            if (env < 0) {
                // New environment ?  check if there is not already an environment with the same ref
                env = getDeploymentId(repo.getName(), repo.getEnvironment(), ref);
            }
            if (env < 0) {
                env = createDeployment(repo.getName(), repo.getEnvironment(), ref, payload);
            }

            if (env > 0) {
                var envUrl = payload.getOrDefault(ENV_URL_SUFFIX, "");
                updateDeployment(repo.getName(), env, repo.getEnvironment(), status.status(), envUrl);
            }
            return env;
        }catch(HttpClientErrorException ghFailure){
            throw new GitHubException(ghFailure);
        }
    }

    private long getDeploymentId(String repo, String env, String ref) {
        var gh = appProperties.getGithub();
        var uriBuilder=UriComponentsBuilder.fromHttpUrl(gh.getGithubApi())
                .pathSegment("repos", gh.getOrgs(), repo, "deployments")
                .queryParam("ref", ref)
                .queryParam("environment", env);

        var ret = restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.GET, null, JsonNode.class);

        var body = ret.getBody();
        if (body != null && body.size() > 0) {
            return body.get(0).get("id").asLong();
        }

        return -1;
    }

    private long createDeployment(String repo, String environment, String ref, Map<String, String> payload) {
        String key= repo + "|"+environment + "|" + ref;
        if (cachedError.contains(key)) {
            throw new GitHubException(422, "An error 422 was returned by GitHub on this exact same request. We will not try again until a small duration.");
        }
        var gh = appProperties.getGithub();
        var uriBuilder=UriComponentsBuilder.fromHttpUrl(gh.getGithubApi())
                .pathSegment("repos", gh.getOrgs(), repo, "deployments");

        ObjectNode json = mapper.createObjectNode();

        var payloadNode = mapper.createObjectNode();
        payload.forEach(payloadNode::put);

        json.put("ref", ref);
        json.put("environment", environment);
        json.put("auto_merge", false);
        json.set("required_contexts", mapper.createArrayNode());
        json.set("payload", payloadNode);
        json.put("description", "Deployment triggered via Flux");
        json.put("transient_environment", false);
        json.put("production_environment", false);

        var requestEntity = new HttpEntity<>(json);
        try {
            var ret = restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.POST, requestEntity, JsonNode.class);
            var body = ret.getBody();
            if (body == null) {
                return -1;
            } else if (body.has("id")){
                return body.get("id").asLong();
            } else {
                String msg;
                if (body.has("message")){
                    msg="Error when creating deployment: "+ body.get("message").asText();
                } else {
                    msg="Error when creating deployment.";
                }
                throw HttpClientErrorException.create(HttpStatusCode.valueOf(422), msg, ret.getHeaders(), msg.getBytes(Charset.defaultCharset()), Charset.defaultCharset());
            }
        } catch (HttpClientErrorException.NotFound ned) {
            // Not found => the ref is invalid or something like that.
            log.error("Unable to create deployment for {} on repo {}", ref, repo);
            return -1;
        } catch (HttpClientErrorException clientError){
            log.error("Unable to create deployment for {} on repo {} and environment {}: {}/{}", ref, repo, environment, clientError.getStatusCode(), clientError.getStatusText());
            // If it is a 422, it means that ref is invalid.
            if (clientError.getStatusCode().value()==422) {
                cachedError.add(key);
                if (cachedError.size() > 1000) {
                    cachedError.removeFirst();
                }
            }
            throw clientError;
        }
    }

    private void updateDeployment(String repo, long deployId, String environment, KubeObjectStatus status, String envUrl) {
        var gh = appProperties.getGithub();
        var uriBuilder=UriComponentsBuilder.fromHttpUrl(gh.getGithubApi())
                .pathSegment("repos", gh.getOrgs(), repo, "deployments", String.valueOf(deployId),"statuses");

        // GH State:
        // error, failure, inactive, in_progress, queued, pending, or success
        String state =
                switch (status) {
                    case IN_PROGRESS -> "in_progress";
                    case CURRENT -> "success";
                    case FAILED -> "failure";
                    case TERMINATING -> "inactive";
                    case UNKNOWN -> "pending";
                };


        ObjectNode json = mapper.createObjectNode();
        json.put("state", state);
        json.put("description", "Deployment via flux");
        json.put("environment", environment);

        if (status != KubeObjectStatus.IN_PROGRESS && status != KubeObjectStatus.UNKNOWN) {
            json.put("auto_inactive", true);
            if (Strings.isNotBlank(envUrl)) {
                json.put("environment_url", envUrl);
            }
        }

        var requestEntity = new HttpEntity<>(json);
        restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.POST, requestEntity, JsonNode.class);
    }
}
