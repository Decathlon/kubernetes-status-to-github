package com.decathlon.github.kubernetesstatus.model;

public final class GithubDeploymentRef  {
    private GithubDeploymentRef() {
        // Private constructor
    }

    public static final String GROUP = "github.decathlon.com";
    public static final String CURRENT_VERSION = "v1beta1";
    public static final String KIND = "GitHubDeployment";
    public static final String SINGULAR = "githubdeployment";
    public static final String PLURAL = "githubdeployments";
    public static final String SHORT_NAME = "ghd";
}
