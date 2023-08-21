/*

Copyright  The Kubernetes Authors.

Licensed under the Apache License, Version 2.0 (the "License");

you may not use this file except in compliance with the License.

You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software

distributed under the License is distributed on an "AS IS" BASIS,

WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and

limitations under the License.

*/
package com.decathlon.github.kubernetesstatus.service.kstatus.internal;

import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectResult;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectStatus;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import org.apache.logging.log4j.util.Strings;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.decathlon.github.kubernetesstatus.service.kstatus.internal.Conditions.ConditionStatus.FALSE;
import static com.decathlon.github.kubernetesstatus.service.kstatus.internal.Conditions.ConditionStatus.TRUE;

public class Core {
    private static final String ON_DELETE_UPDATE_STRATEGY = "OnDelete";

    // How long a pod can be unscheduled before it is reported as
    // unschedulable.
    private static final int SCHEDULE_WINDOW = 15;

    private Core() {
        // no instance
    }


    public static KubeObjectResult getLegacyConditions(DynamicKubernetesObject obj) {
        var groupVersion = obj.getApiVersion().split("/");
        var key = groupVersion.length == 1 ? obj.getKind() : groupVersion[0] + "/" + obj.getKind();

        return switch (key) {
            case "Service" -> serviceConditions(obj);
            case "Pod" -> podConditions(obj);
            case "Secret" -> alwaysReady();
            case "PersistentVolumeClaim" -> pvcConditions(obj);
            case "apps/StatefulSet" -> stsConditions(obj);
            case "apps/DaemonSet" -> daemonsetConditions(obj);
            case "extensions/DaemonSet" -> daemonsetConditions(obj);
            case "apps/Deployment" -> deploymentConditions(obj);
            case "extensions/Deployment" -> deploymentConditions(obj);
            case "apps/ReplicaSet" -> replicasetConditions(obj);
            case "extensions/ReplicaSet" -> replicasetConditions(obj);
            case "policy/PodDisruptionBudget" -> pdbConditions();
            case "batch/CronJob" -> alwaysReady();
            case "ConfigMap" -> alwaysReady();
            case "batch/Job" -> jobConditions(obj);
            case "apiextensions.k8s.io/CustomResourceDefinition" -> crdConditions(obj);
            default -> null;
        };
    }


    /**
     * alwaysReady Used for resources that are always ready
     */
    private static KubeObjectResult alwaysReady() {
        return new KubeObjectResult(KubeObjectStatus.CURRENT, "Resource is always ready");
    }

    /**
     * stsConditions return standardized Conditions for Statefulset
     * <p>
     * StatefulSet does define the .status.conditions property, but the controller never
     * actually sets any Conditions. Thus, status must be computed only based on the other
     * properties under .status. We don't have any way to find out if a reconcile for a
     * StatefulSet has failed.
     */
    private static KubeObjectResult stsConditions(DynamicKubernetesObject obj) {
        // updateStrategy==ondelete is a user managed statefulset.
        var updateStrategy = Utils.findStringOrDefault(obj.getRaw(), "", "spec", "updateStrategy", "type");
        if (Objects.equals(updateStrategy, ON_DELETE_UPDATE_STRATEGY)) {
            return new KubeObjectResult(KubeObjectStatus.CURRENT, "StatefulSet is using the ondelete update strategy");
        }

        // Replicas
        var specReplicas = Utils.findIntOrDefault(obj.getRaw(), 1, "spec", "replicas");
        var readyReplicas = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "readyReplicas");
        var currentReplicas = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "currentReplicas");
        var updatedReplicas = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "updatedReplicas");
        var statusReplicas = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "replicas");
        var partition = Utils.findIntOrDefault(obj.getRaw(), -1, "spec", "updateStrategy", "rollingUpdate", "partition");

        if (specReplicas > statusReplicas) {
            var message = String.format("Replicas: %d/%d", statusReplicas, specReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (specReplicas > readyReplicas) {
            var message = String.format("Ready: %d/%d", readyReplicas, specReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (statusReplicas > specReplicas) {
            var message = String.format("Pending termination: %d", statusReplicas - specReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        // https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/#partitions
        if (partition != -1) {
            if (updatedReplicas < (specReplicas - partition)) {
                var message = String.format("updated: %d/%d", updatedReplicas, specReplicas - partition);
                return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
            }
            // Partition case All ok
            return new KubeObjectResult(KubeObjectStatus.CURRENT, String.format("Partition rollout complete. updated: %d", updatedReplicas));
        }

        if (specReplicas > currentReplicas) {
            var message = String.format("current: %d/%d", currentReplicas, specReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        // Revision
        var currentRevision = Utils.findStringOrDefault(obj.getRaw(), "", "status", "currentRevision");
        var updatedRevision = Utils.findStringOrDefault(obj.getRaw(), "", "status", "updateRevision");
        if (!Objects.equals(currentRevision, updatedRevision)) {
            var message = "Waiting for updated revision to match current";
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        // All ok
        return new KubeObjectResult(KubeObjectStatus.CURRENT, String.format("All replicas scheduled as expected. Replicas: %d", statusReplicas));
    }

    /**
     * deploymentConditions return standardized Conditions for Deployment.
     * <p>
     * For Deployments, we look at .status.conditions as well as the other properties
     * under .status. Status will be Failed if the progress deadline has been exceeded.
     */
    private static KubeObjectResult deploymentConditions(DynamicKubernetesObject obj) {
        var progressing = false;

        // Check if progressDeadlineSeconds is set. If not, the controller will not set
        // the `Progressing` condition, so it will always consider a deployment to be
        // progressing. The use of math.MaxInt32 is due to special handling in the
        // controller:
        // https://github.com/kubernetes/kubernetes/blob/a3ccea9d8743f2ff82e41b6c2af6dc2c41dc7b10/pkg/controller/deployment/util/deployment_util.go#L886
        var progressDeadline = Utils.findIntOrDefault(obj.getRaw(), Integer.MAX_VALUE, "spec", "progressDeadlineSeconds");
        if (progressDeadline == Integer.MAX_VALUE) {
            progressing = true;
        }

        var available = false;

        var objc = Utils.getObjectWithConditions(obj.getRaw());
        var conds = objc!=null?objc.status().conditions():Collections.<Conditions.BasicCondition>emptyList();

        for (var c : conds) {
            switch (c.type()) {
                case "Progressing": // appsv1.DeploymentProgressing:
                    // https://github.com/kubernetes/kubernetes/blob/a3ccea9d8743f2ff82e41b6c2af6dc2c41dc7b10/pkg/controller/deployment/progress.go#L52
                    if (Objects.equals(c.reason(), "ProgressDeadlineExceeded")) {
                        return new KubeObjectResult(KubeObjectStatus.FAILED, "Progress deadline exceeded");
                    }
                    if ((c.status() == TRUE) && (Objects.equals(c.reason(), "NewReplicaSetAvailable"))) {
                        progressing = true;
                    }
                    break;
                case "Available": // appsv1.DeploymentAvailable:
                    if (c.status() == TRUE) {
                        available = true;
                    }
                    break;
                default:
                    // Ignore other conditions
            }
        }

        // replicas
        var specReplicas = Utils.findIntOrDefault(obj.getRaw(), 1, "spec", "replicas"); // Controller uses 1 as default if not specified.
        var statusReplicas = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "replicas");
        var updatedReplicas = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "updatedReplicas");
        var readyReplicas = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "readyReplicas");
        var availableReplicas = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "availableReplicas");

        // TODO spec.replicas zero case ??

        if (specReplicas > statusReplicas) {
            var message = String.format("replicas: %d/%d", statusReplicas, specReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (specReplicas > updatedReplicas) {
            var message = String.format("Updated: %d/%d", updatedReplicas, specReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (statusReplicas > specReplicas) {
            var message = String.format("Pending termination: %d", statusReplicas - specReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (updatedReplicas > availableReplicas) {
            var message = String.format("Available: %d/%d", availableReplicas, updatedReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (specReplicas > readyReplicas) {
            var message = String.format("Ready: %d/%d", readyReplicas, specReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        // check conditions
        if (!progressing) {
            var message = "ReplicaSet not Available";
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }
        if (!available) {
            var message = "Deployment not Available";
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }
        // All ok
        return new KubeObjectResult(KubeObjectStatus.CURRENT, String.format("Deployment is available. Replicas: %d", statusReplicas));

    }

    /**
     * replicasetConditions return standardized Conditions for Replicaset
     */
    private static KubeObjectResult replicasetConditions(DynamicKubernetesObject obj) {
        var objc = Utils.getObjectWithConditions(obj.getRaw());
        var conds = objc!=null?objc.status().conditions():Collections.<Conditions.BasicCondition>emptyList();

        for (var c : conds) {
            // https://github.com/kubernetes/kubernetes/blob/a3ccea9d8743f2ff82e41b6c2af6dc2c41dc7b10/pkg/controller/replicaset/replica_set_utils.go
            if (Objects.equals(c.type(), "ReplicaFailure") && c.status() == TRUE) {
                var message = "Replica Failure condition. Check Pods";
                return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
            }
        }

        // Replicas
        var specReplicas = Utils.findIntOrDefault(obj.getRaw(), 1, "spec", "replicas"); // Controller uses 1 as default if not specified.
        var statusReplicas = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "replicas");
        var readyReplicas = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "readyReplicas");
        var availableReplicas = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "availableReplicas");
        var fullyLabelledReplicas = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "fullyLabeledReplicas");

        if (specReplicas > fullyLabelledReplicas) {
            var message = String.format("Labelled: %d/%d", fullyLabelledReplicas, specReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (specReplicas > availableReplicas) {
            var message = String.format("Available: %d/%d", availableReplicas, specReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (specReplicas > readyReplicas) {
            var message = String.format("Ready: %d/%d", readyReplicas, specReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (statusReplicas > specReplicas) {
            var message = String.format("Pending termination: %d", statusReplicas - specReplicas);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }
        // All ok
        return new KubeObjectResult(KubeObjectStatus.CURRENT, String.format("ReplicaSet is available. Replicas: %d", statusReplicas));
    }

    /**
     * daemonsetConditions return standardized Conditions for DaemonSet
     */
    private static KubeObjectResult daemonsetConditions(DynamicKubernetesObject obj) {
        // replicas
        var desiredNumberScheduled = Utils.findIntOrDefault(obj.getRaw(), -1, "status", "desiredNumberScheduled");
        var currentNumberScheduled = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "currentNumberScheduled");
        var updatedNumberScheduled = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "updatedNumberScheduled");
        var numberAvailable = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "numberAvailable");
        var numberReady = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "numberReady");

        if (desiredNumberScheduled == -1) {
            var message = "Missing .status.desiredNumberScheduled";
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (desiredNumberScheduled > currentNumberScheduled) {
            var message = String.format("Current: %d/%d", currentNumberScheduled, desiredNumberScheduled);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (desiredNumberScheduled > updatedNumberScheduled) {
            var message = String.format("Updated: %d/%d", updatedNumberScheduled, desiredNumberScheduled);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (desiredNumberScheduled > numberAvailable) {
            var message = String.format("Available: %d/%d", numberAvailable, desiredNumberScheduled);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        if (desiredNumberScheduled > numberReady) {
            var message = String.format("Ready: %d/%d", numberReady, desiredNumberScheduled);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }

        // All ok
        return new KubeObjectResult(KubeObjectStatus.CURRENT, String.format("All replicas scheduled as expected. Replicas: %d", desiredNumberScheduled));
    }

    // pvcConditions return standardized Conditions for PVC
    private static KubeObjectResult pvcConditions(DynamicKubernetesObject obj) {

        var phase = Utils.findStringOrDefault(obj.getRaw(), "unknown", "status", "phase");
        if (!Objects.equals(phase, "Bound")) { // corev1.ClaimBound
            var message = String.format("PVC is not Bound. phase: %s", phase);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }
        // All ok
        return new KubeObjectResult(KubeObjectStatus.CURRENT, "PVC is Bound");
    }

    /**
     * podConditions return standardized Conditions for Pod
     */
    private static KubeObjectResult podConditions(DynamicKubernetesObject obj) {
        var objc = Utils.getObjectWithConditions(obj.getRaw());
        var conds = objc!=null?objc.status().conditions():Collections.<Conditions.BasicCondition>emptyList();

        var phase = Utils.findStringOrDefault(obj.getRaw(), "", "status", "phase");

        switch (phase) {
            case "Succeeded":
                return new KubeObjectResult(KubeObjectStatus.CURRENT, "Pod has completed successfully");
            case "Failed":
                return new KubeObjectResult(KubeObjectStatus.CURRENT, "Pod has completed, but not successfully");
            case "Running":
                if (Utils.hasConditionWithStatus(conds, "Ready", TRUE)) {
                    return new KubeObjectResult(KubeObjectStatus.CURRENT, "Pod is Ready");
                }

                var crashingContainers = getCrashLoopingContainers(obj);

                if (crashingContainers.crashing) {
                    return new KubeObjectResult(KubeObjectStatus.FAILED, String.format("Containers in CrashLoop state: %s", String.join(", ", crashingContainers.container)));
                }

                return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, "Pod is running but is not Ready");
            case "Pending":
                var c = Utils.getConditionWithStatus(conds, "PodScheduled", Conditions.ConditionStatus.FALSE);
                if (c.isPresent() && "Unschedulable".equals(c.get().reason())) {
                    if (obj.getMetadata() != null && obj.getMetadata().getCreationTimestamp() != null) {
                        if (OffsetDateTime.now().minus(SCHEDULE_WINDOW, ChronoUnit.SECONDS).isBefore(obj.getMetadata().getCreationTimestamp())) {
                            // We give the pod 15 seconds to be scheduled before we report it
                            // as unschedulable.
                            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, "Pod has not been scheduled");
                        }
                    }
                    return new KubeObjectResult(KubeObjectStatus.FAILED, "Pod could not be scheduled");
                }
                return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, "Pod is in the Pending phase");
            default:
                // If the controller hasn't observed the pod yet, there is no phase. We consider this as it
                // still being in progress.
                if (Strings.isEmpty(phase)) {
                    return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, "Pod phase not available");
                }
                return new KubeObjectResult(KubeObjectStatus.UNKNOWN, String.format("unknown phase %s", phase));
        }
    }

    private record CrashingContainers(List<String> container, boolean crashing) {
    }

    private static CrashingContainers getCrashLoopingContainers(DynamicKubernetesObject obj) {
        var raw = obj.getRaw();
        var containers = Utils.findNode(raw, "status", "containerStatuses");
        if (containers == null || !containers.isJsonArray()) {
            return new CrashingContainers(Collections.emptyList(), false);
        }

        var crashingContainers = new ArrayList<String>();

        for (var co : containers.getAsJsonArray()) {
            if (!co.isJsonObject())
                continue;
            var c = co.getAsJsonObject();

            if (!c.has("name") || !c.has("state")) {
                continue;
            }
            var name = c.get("name").getAsString();
            var state = c.get("state").getAsJsonObject();

            if (!state.has("waiting")) {
                continue;
            }
            var waiting = state.get("waiting").getAsJsonObject();

            if (!waiting.has("reason")) {
                continue;
            }
            var reason = waiting.get("reason").getAsString();

            if (Objects.equals(reason, "CrashLoopBackOff")) {
                crashingContainers.add(name);
            }
        }

        return new CrashingContainers(crashingContainers, !crashingContainers.isEmpty());
    }

    /**
     * pdbConditions computes the status for PodDisruptionBudgets. A PDB
     * is currently considered Current if the disruption controller has
     * observed the latest version of the PDB resource and has computed
     * the AllowedDisruptions. PDBs do have ObservedGeneration in the
     * Status object, so if this function gets called we know that
     * the controller has observed the latest changes.
     * The disruption controller does not set any conditions if
     * computing the AllowedDisruptions fails (and there are many ways
     * it can fail), but there is PR against OSS Kubernetes to address
     * this: https://github.com/kubernetes/kubernetes/pull/86929
     */
    private static KubeObjectResult pdbConditions() {
        // All ok
        return new KubeObjectResult(KubeObjectStatus.CURRENT, "AllowedDisruptions has been computed.");
    }

    /**
     * jobConditions return standardized Conditions for Job
     * <p>
     * A job will have the InProgress status until it starts running. Then it will have the Current
     * status while the job is running and after it has been completed successfully. It
     * will have the Failed status if it the job has failed.
     */
    private static KubeObjectResult jobConditions(DynamicKubernetesObject obj) {
        var parallelism = Utils.findIntOrDefault(obj.getRaw(), 1, "spec", "parallelism");
        var completions = Utils.findIntOrDefault(obj.getRaw(), parallelism, "spec", "completions");
        var succeeded = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "succeeded");
        var active = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "active");
        var failed = Utils.findIntOrDefault(obj.getRaw(), 0, "status", "failed");
        var starttime = Utils.findStringOrDefault(obj.getRaw(), "", "status", "startTime");

        // Conditions
        // https://github.com/kubernetes/kubernetes/blob/master/pkg/controller/job/utils.go#L24
        var objc = Utils.getObjectWithConditions(obj.getRaw());
        var conds = objc != null ? objc.status().conditions() : Collections.<Conditions.BasicCondition>emptyList();
        for (var c : conds) {
            switch (c.type()) {
                case "Complete":
                    if (c.status() == TRUE) {
                        var message = String.format("Job Completed. succeeded: %d/%d", succeeded, completions);
                        return new KubeObjectResult(KubeObjectStatus.CURRENT, message);
                    }
                    break;
                case "Failed":
                    if (c.status() == TRUE) {
                        return new KubeObjectResult(KubeObjectStatus.FAILED, String.format("Job Failed. failed: %d/%d", failed, completions));
                    }
                    break;
                default:
                    // Nothing to do

            }
        }

        // replicas
        if (Strings.isEmpty(starttime)) {
            var message = "Job not started";
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }
        return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, String.format("Job in progress. success:%d, active: %d, failed: %d", succeeded, active, failed));
    }

    /**
     * serviceConditions return standardized Conditions for Service
     */
    private static KubeObjectResult serviceConditions(DynamicKubernetesObject obj) {
        var specType = Utils.findStringOrDefault(obj.getRaw(), "ClusterIP", "spec", "type");
        var specClusterIP = Utils.findStringOrDefault(obj.getRaw(), "", "spec", "clusterIP");

        if (Objects.equals(specType, "LoadBalancer") && Strings.isEmpty(specClusterIP)) {
            var message = "ClusterIP not set. Service type: LoadBalancer";
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, message);
        }
        return new KubeObjectResult(KubeObjectStatus.CURRENT, "Service is ready");
    }

    private static KubeObjectResult crdConditions(DynamicKubernetesObject obj) {
        var objc = Utils.getObjectWithConditions(obj.getRaw());
        var conds = objc != null ? objc.status().conditions() : Collections.<Conditions.BasicCondition>emptyList();
        for (var c : conds) {
            if ("NamesAccepted".equals(c.type()) && (c.status() == FALSE)) {
                return new KubeObjectResult(KubeObjectStatus.FAILED, c.message());
            }
            if ("Established".equals(c.type())) {
                if (c.status() == FALSE && !"Installing".equals(c.reason())) {
                    return new KubeObjectResult(KubeObjectStatus.FAILED, c.message());
                }
                if (c.status() == TRUE) {
                    return new KubeObjectResult(KubeObjectStatus.CURRENT, "CRD is established");
                }
            }
        }
        return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, "Install in progress");
    }
}
