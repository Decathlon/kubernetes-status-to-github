package com.decathlon.github.kubernetesstatus.data.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@ConfigurationProperties("app")
@Component
@Data
public class AppProperties {
    private AppMode mode;
    private TransferProperties transfer;
    private GitHubProperties github;
    private KubernetesProperties kubernetes;

    @Data
    public static class GitHubProperties {
        private String orgs;
        private String githubApi;
        private String token;
        private GithubAppProperties app;
    }

    @Data
    public static class GithubAppProperties {
        private long id;
        private String privateKey;
    }

    @Data
    public static class KubernetesProperties {
        private Duration refresh=Duration.ofSeconds(60);

        public long getRefreshInSecond() {
            return refresh.get(java.time.temporal.ChronoUnit.SECONDS);
        }
    }

    @Data
    public static class TransferProperties {
        private String host;
        private String oauthRegistration;
    }
}
