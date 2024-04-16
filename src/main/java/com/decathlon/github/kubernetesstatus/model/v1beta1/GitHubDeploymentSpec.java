package com.decathlon.github.kubernetesstatus.model.v1beta1;

import io.swagger.annotations.ApiModel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
@ApiModel(description = "GitHubDeployment spec part.")
public class GitHubDeploymentSpec {
    private NamespacedObject sourceRef;
    private List<NamespacedObject> additionnalRef;
    private ExtractRule extract;
    private RepositoryDetail repository;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
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
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder(setterPrefix = "with")
    @ApiModel(description = """
    Rule to extract ref to use in GH deployment. A container name or a json path is mandatory regexp is optional
    if a regexp is declared, then the jsonpath or tag referenced by containerName will be parsed with this regexp
    and a named group 'ref' has to be captured.
    """)
    public static class ExtractRule {
        private String containerName;
        private String jsonPath;
        /**
         * Have to capture a 'sha1' or 'pr' or 'branch' or 'tag' or 'ref'. the group name MUST BE 'ref'
         */
        private String regexp;
        private String template;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder(setterPrefix = "with")
    @ApiModel(description = "Repository to push deployment to.")
    public static class RepositoryDetail {
        @NotNull
        private String name;
        @NotNull
        private String environment;
    }
}
