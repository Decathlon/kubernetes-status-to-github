package com.decathlon.github.kubernetesstatus.model.v1beta1;

import com.decathlon.github.kubernetesstatus.model.GithubDeploymentRef;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;

@Group(GithubDeploymentRef.GROUP)
@Version(value = "v1beta1", served = true, storage = true)
@Kind(GithubDeploymentRef.KIND)
@Singular(GithubDeploymentRef.SINGULAR)
@Plural(GithubDeploymentRef.PLURAL)
@ShortNames(GithubDeploymentRef.SHORT_NAME)
public class GitHubDeploymentCRD extends CustomResource<GitHubDeploymentSpec, GitHubDeploymentStatus> implements Namespaced {
}
