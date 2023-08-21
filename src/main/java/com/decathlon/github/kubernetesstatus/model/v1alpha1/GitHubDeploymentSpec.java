package com.decathlon.github.kubernetesstatus.model.v1alpha1;

import io.swagger.annotations.ApiModel;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;



@Data
@Builder(setterPrefix = "with")
@ApiModel(description = "GitHubDeployment spec part.")
public class GitHubDeploymentSpec {
    private NamespacedObject sourceRef;
    private ExtractRule extract;
    private RepositoryDetail repository;

    @Data
    @Builder(setterPrefix = "with")
    @ApiModel(description = "Namespace description of target.")
    public static class NamespacedObject  {
        /**
          API version of the referent,
          if not specified the Kubernetes preferred version will be used
        */
        private String apiVersion;

        // Kind of the referent
        // +required
        @NotNull
        private String kind;

        // Name of the referent
        @NotNull
        private String name;

        // Namespace of the referent,
        // when not specified it acts as LocalObjectReference
        @NotNull
        private String namespace;
    }

    @Data
    @Builder(setterPrefix = "with")
    @ApiModel(description = "Rule to extract ref to use in GH deployment.")
    public static class ExtractRule {
        @NotNull
        private String containerName;
        /**
         * Have to capture 'sha1' or 'pr' or 'branch' or 'tag' or 'ref'
         */
        @NotNull
        private String regexp;
    }

    @Data
    @Builder(setterPrefix = "with")
    @ApiModel(description = "Repository to push deployment to.")
    public static class RepositoryDetail {
        @NotNull
        private String name;
        @NotNull
        private String environment;
    }
}
