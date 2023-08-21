package com.decathlon.github.kubernetesstatus.kstatus;

import com.decathlon.github.kubernetesstatus.service.kstatus.Status;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectStatus;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class StatusComputeTests {

    public void runStatusTest(String name, String spec, KubeObjectStatus expected) {
        DynamicKubernetesObject obj = Dynamics.newFromYaml(spec);
        assertThat(Status.compute(obj).status()).as(name).isEqualTo(expected);
    }


    String podNoStatus = """
            apiVersion: v1
            kind: Pod
            metadata:
               generation: 1
               name: test
            """;

    String podReady = """
            apiVersion: v1
            kind: Pod
            metadata:
               generation: 1
               name: test
               namespace: qual
            status:
               conditions:
                - type: Ready
                  status: "True"
               phase: Running
            """;

    String podCompletedOK = """
            apiVersion: v1
            kind: Pod
            metadata:
               generation: 1
               name: test
               namespace: qual
            status:
               phase: Succeeded
               conditions:
                - type: Ready
                  status: "False"
                  reason: PodCompleted
            """;

    String podCompletedFail = """
            apiVersion: v1
            kind: Pod
            metadata:
               generation: 1
               name: test
               namespace: qual
            status:
               phase: Failed
               conditions:
                - type: Ready
                  status: "False"
                  reason: PodCompleted
            """;

    String podBeingScheduled = """
            apiVersion: v1
            kind: Pod
            metadata:
               creationTimestamp: %s
               generation: 1
               name: test
               namespace: qual
            status:
               phase: Pending
               conditions:
                - type: PodScheduled
                  status: "False"
                  reason: Unschedulable
            """;

    String podUnschedulable = """
            apiVersion: v1
            kind: Pod
            metadata:
               generation: 1
               name: test
               namespace: qual
            status:
               phase: Pending
               conditions:
                - type: PodScheduled
                  status: "False"
                  reason: Unschedulable
            """;

    String podCrashLooping = """
            apiVersion: v1
            kind: Pod
            metadata:
               generation: 1
               name: test
               namespace: qual
            status:
               phase: Running
               conditions:
                - type: PodScheduled
                  status: "False"
                  reason: Unschedulable
               containerStatuses:
                - name: nginx
                  state:
                     waiting:
                        reason: CrashLoopBackOff
            """;


    @Test
        // Test coverage using GetConditions
    void testPodStatus() {
        runStatusTest("podNoStatus", podNoStatus, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("podReady", podReady, KubeObjectStatus.CURRENT);
        runStatusTest("podCompletedSuccessfully", podCompletedOK, KubeObjectStatus.CURRENT);
        runStatusTest("podCompletedFailed", podCompletedFail, KubeObjectStatus.CURRENT);
        runStatusTest("podBeingScheduled", String.format(podBeingScheduled, OffsetDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)), KubeObjectStatus.IN_PROGRESS);
        runStatusTest("podUnschedulable", podUnschedulable, KubeObjectStatus.FAILED);
        runStatusTest("podCrashLooping", podCrashLooping, KubeObjectStatus.FAILED);
    }


    String pvcNoStatus = """
            apiVersion: v1
            kind: PersistentVolumeClaim
            metadata:
               generation: 1
               name: test
            """;
    String pvcBound = """
            apiVersion: v1
            kind: PersistentVolumeClaim
            metadata:
               generation: 1
               name: test
               namespace: qual
            status:
               phase: Bound
            """;

    @Test
    void testPVCStatus() {
        runStatusTest("pvcNoStatus", pvcNoStatus, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("pvcBound", pvcBound, KubeObjectStatus.CURRENT);
    }


    String stsNoStatus = """
            apiVersion: apps/v1
            kind: StatefulSet
            metadata:
               generation: 1
               name: test
            """;
    String stsBadStatus = """
            apiVersion: apps/v1
            kind: StatefulSet
            metadata:
               generation: 1
               name: test
               namespace: qual
            status:
               observedGeneration: 1
               currentReplicas: 1
            """;

    String stsOK = """
            apiVersion: apps/v1
            kind: StatefulSet
            metadata:
               generation: 1
               name: test
               namespace: qual
            spec:
               replicas: 4
            status:
               observedGeneration: 1
               currentReplicas: 4
               readyReplicas: 4
               replicas: 4
            """;

    String stsLessReady = """
            apiVersion: apps/v1
            kind: StatefulSet
            metadata:
               generation: 1
               name: test
               namespace: qual
            spec:
               replicas: 4
            status:
               observedGeneration: 1
               currentReplicas: 4
               readyReplicas: 2
               replicas: 4
            """;
    String stsLessCurrent = """
            apiVersion: apps/v1
            kind: StatefulSet
            metadata:
               generation: 1
               name: test
               namespace: qual
            spec:
               replicas: 4
            status:
               observedGeneration: 1
               currentReplicas: 2
               readyReplicas: 4
               replicas: 4
            """;
    String stsExtraPods = """
            apiVersion: apps/v1
            kind: StatefulSet
            metadata:
               generation: 1
               name: test
               namespace: qual
            spec:
               replicas: 4
            status:
               observedGeneration: 1
               currentReplicas: 4
               readyReplicas: 4
               replicas: 8
            """;

    String stsOnDelete = """
            apiVersion: apps/v1
            kind: StatefulSet
            metadata:
               generation: 1
               name: test
               namespace: qual
            spec:
               updateStrategy:
                 type: OnDelete
            """;

    String stsRollingPartitionProgress = """
            apiVersion: apps/v1
            kind: StatefulSet
            metadata:
              generation: 1
              name: test
              namespace: qual
            spec:
              replicas: 4
              updateStrategy:
                rollingUpdate:
                  partition: 2
            status:
              updatedReplicas: 0
              replicas: 4
              readyReplicas: 4
            """;

    String stsRollingPartitionOk = """
            apiVersion: apps/v1
            kind: StatefulSet
            metadata:
              generation: 1
              name: test
              namespace: qual
            spec:
              replicas: 4
              updateStrategy:
                rollingUpdate:
                  partition: 2
            status:
              updatedReplicas: 4
              replicas: 4
              readyReplicas: 4
            """;

    @Test
    void testStsStatus() {
        runStatusTest("stsNoStatus", stsNoStatus, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("stsBadStatus", stsBadStatus, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("stsOK", stsOK, KubeObjectStatus.CURRENT);
        runStatusTest("stsLessReady", stsLessReady, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("stsLessCurrent", stsLessCurrent, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("stsExtraPods", stsExtraPods, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("stsOnDelete", stsOnDelete, KubeObjectStatus.CURRENT);
        runStatusTest("stsRollingPartitionProgress", stsRollingPartitionProgress, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("stsRollingPartitionOk", stsRollingPartitionOk, KubeObjectStatus.CURRENT);
    }


    String dsNoStatus = """
            apiVersion: apps/v1
            kind: DaemonSet
            metadata:
               name: test
               generation: 1
            """;

    String dsOldNoStatus = """
            apiVersion: extensions/v1
            kind: DaemonSet
            metadata:
               name: test
               generation: 1
            """;

    String dsBadStatus = """
            apiVersion: apps/v1
            kind: DaemonSet
            metadata:
               name: test
               namespace: qual
               generation: 1
            status:
               observedGeneration: 1
               currentReplicas: 1
            """;

    String dsOK = """
            apiVersion: apps/v1
            kind: DaemonSet
            metadata:
               name: test
               namespace: qual
               generation: 1
            status:
               desiredNumberScheduled: 4
               currentNumberScheduled: 4
               updatedNumberScheduled: 4
               numberAvailable: 4
               numberReady: 4
               observedGeneration: 1
            """;

    String dsLessReady = """
            apiVersion: apps/v1
            kind: DaemonSet
            metadata:
               name: test
               namespace: qual
               generation: 1
            status:
               observedGeneration: 1
               desiredNumberScheduled: 4
               currentNumberScheduled: 4
               updatedNumberScheduled: 4
               numberAvailable: 4
               numberReady: 2
            """;
    String dsLessAvailable = """
            apiVersion: apps/v1
            kind: DaemonSet
            metadata:
               name: test
               namespace: qual
               generation: 1
            status:
               observedGeneration: 1
               desiredNumberScheduled: 4
               currentNumberScheduled: 4
               updatedNumberScheduled: 4
               numberAvailable: 2
               numberReady: 4
            """;

    String dsTooMuchAvailable = """
            apiVersion: apps/v1
            kind: DaemonSet
            metadata:
               name: test
               namespace: qual
               generation: 1
            status:
               observedGeneration: 1
               desiredNumberScheduled: 5
               currentNumberScheduled: 4
               updatedNumberScheduled: 4
               numberAvailable: 4
               numberReady: 4
            """;

    String dsTooUpdatedAvailable = """
            apiVersion: apps/v1
            kind: DaemonSet
            metadata:
               name: test
               namespace: qual
               generation: 1
            status:
               observedGeneration: 1
               desiredNumberScheduled: 4
               currentNumberScheduled: 4
               updatedNumberScheduled: 3
               numberAvailable: 2
               numberReady: 4
            """;

    @Test
    void testDaemonsetStatus() {
        runStatusTest("dsNoStatus", dsNoStatus, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("dsOldNoStatus", dsOldNoStatus, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("dsBadStatus", dsBadStatus, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("dsOK", dsOK, KubeObjectStatus.CURRENT);
        runStatusTest("dsLessReady", dsLessReady, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("dsLessAvailable", dsLessAvailable, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("dsTooMuchAvailable", dsTooMuchAvailable, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("dsTooUpdatedAvailable", dsTooUpdatedAvailable, KubeObjectStatus.IN_PROGRESS);
    }

    String depNoStatus = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
               name: test
               generation: 1
            """;

    String depOldNoStatus = """
            apiVersion: extensions/v1
            kind: Deployment
            metadata:
               name: test
               generation: 1
            """;

    String depOK = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
               name: test
               generation: 1
               namespace: qual
            status:
               observedGeneration: 1
               updatedReplicas: 1
               readyReplicas: 1
               availableReplicas: 1
               replicas: 1
               conditions:
                - type: Progressing 
                  status: "True"
                  reason: NewReplicaSetAvailable
                - type: Available 
                  status: "True"
            """;

    String depNotProgressing = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
               name: test
               generation: 1
               namespace: qual
            spec:
               progressDeadlineSeconds: 45
            status:
               observedGeneration: 1
               updatedReplicas: 1
               readyReplicas: 1
               availableReplicas: 1
               replicas: 1
               observedGeneration: 1
               conditions:
                - type: Progressing 
                  status: "False"
                  reason: Some reason
                - type: Available 
                  status: "True"
            """;

    String depNoProgressDeadlineSeconds = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
               name: test
               generation: 1
               namespace: qual
            status:
               observedGeneration: 1
               updatedReplicas: 1
               readyReplicas: 1
               availableReplicas: 1
               replicas: 1
               observedGeneration: 1
               conditions:
                - type: Available
                  status: "True"
            """;

    String depNotAvailable = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
               name: test
               generation: 1
               namespace: qual
            status:
               observedGeneration: 1
               updatedReplicas: 1
               readyReplicas: 1
               availableReplicas: 1
               replicas: 1
               observedGeneration: 1
               conditions:
                - type: Progressing 
                  status: "True"
                  reason: NewReplicaSetAvailable
                - type: Available 
                  status: "False"
            """;

    String depReplicas = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: test
              generation: 1
              namespace: qual
            spec:
              replicas: %d
            status:
              observedGeneration: 1
              replicas: %d
              updatedReplicas: %d
              readyReplicas: %d
              availableReplicas: %d
            """;

    @Test
    void testDeploymentStatus() {
        runStatusTest("depNoStatus", depNoStatus, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("depOldNoStatus", depOldNoStatus, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("depOK", depOK, KubeObjectStatus.CURRENT);
        runStatusTest("depNotProgressing", depNotProgressing, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("depNoProgressDeadlineSeconds", depNoProgressDeadlineSeconds, KubeObjectStatus.CURRENT);
        runStatusTest("depNotAvailable", depNotAvailable, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("depReplicasUpd", String.format(depReplicas,1,1,0,2,2), KubeObjectStatus.IN_PROGRESS);
        runStatusTest("depReplicasDown", String.format(depReplicas,1,2,2,2,2), KubeObjectStatus.IN_PROGRESS);
        runStatusTest("depReplicasAvail", String.format(depReplicas,2,2,2,2,1), KubeObjectStatus.IN_PROGRESS);
        runStatusTest("depReplicasAvail", String.format(depReplicas,2,2,2,1,2), KubeObjectStatus.IN_PROGRESS);
    }


    String rsNoStatus = """
            apiVersion: apps/v1
            kind: ReplicaSet
            metadata:
               name: test
               generation: 1
            """;

    String rsOldNoStatus = """
            apiVersion: extensions/v1
            kind: ReplicaSet
            metadata:
               name: test
               generation: 1
            """;

    String rsOK1 = """
            apiVersion: apps/v1
            kind: ReplicaSet
            metadata:
               name: test
               namespace: qual
               generation: 1
            spec:
               replicas: 2
            status:
               observedGeneration: 1
               replicas: 2
               readyReplicas: 2
               availableReplicas: 2
               fullyLabeledReplicas: 2
               conditions:
                - type: ReplicaFailure
                  status: "False"
            """;

    String rsOK2 = """
            apiVersion: apps/v1
            kind: ReplicaSet
            metadata:
               name: test
               namespace: qual
               generation: 1
            spec:
               replicas: 2
            status:
               observedGeneration: 1
               fullyLabeledReplicas: 2
               replicas: 2
               readyReplicas: 2
               availableReplicas: 2
            """;

    String rsLessReady = """
            apiVersion: apps/v1
            kind: ReplicaSet
            metadata:
               name: test
               namespace: qual
               generation: 1
            spec:
               replicas: 4
            status:
               observedGeneration: 1
               replicas: 4
               readyReplicas: 2
               availableReplicas: 4
               fullyLabeledReplicas: 4
            """;

    String rsLessAvailable = """
            apiVersion: apps/v1
            kind: ReplicaSet
            metadata:
               name: test
               namespace: qual
               generation: 1
            spec:
               replicas: 4
            status:
               observedGeneration: 1
               replicas: 4
               readyReplicas: 4
               availableReplicas: 2
               fullyLabeledReplicas: 4
               conditions:
                - type: Random
                  status: "True"
            """;

    String rsPendingTermination = """
            apiVersion: apps/v1
            kind: ReplicaSet
            metadata:
               name: test
               namespace: qual
               generation: 1
            spec:
               replicas: 4
            status:
               observedGeneration: 1
               replicas: 5
               readyReplicas: 4
               availableReplicas: 4
               fullyLabeledReplicas: 4
               conditions:
                - type: Random
                  status: "True"
            """;

    String rsReplicaFailure = """
            apiVersion: apps/v1
            kind: ReplicaSet
            metadata:
               name: test
               namespace: qual
               generation: 1
            spec:
               replicas: 4
            status:
               observedGeneration: 1
               replicas: 4
               readyReplicas: 4
               fullyLabeledReplicas: 4
               availableReplicas: 4
               conditions:
                - type: ReplicaFailure
                  status: "True"
            """;

    @Test
    void testReplicasetStatus() {
        runStatusTest("rsNoStatus", rsNoStatus, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("rsOldNoStatus", rsOldNoStatus, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("rsOK1", rsOK1, KubeObjectStatus.CURRENT);
        runStatusTest("rsOK2", rsOK2, KubeObjectStatus.CURRENT);
        runStatusTest("rsLessAvailable", rsLessAvailable, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("rsLessReady", rsLessReady, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("rsPendingTermination", rsPendingTermination, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("rsReplicaFailure", rsReplicaFailure, KubeObjectStatus.IN_PROGRESS);
    }


    String pdbNotObserved = """
            apiVersion: policy/v1beta1
            kind: PodDisruptionBudget
            metadata:
               generation: 2
               name: test
               namespace: qual
            status:
               observedGeneration: 1
            """;

    String pdbObserved = """
            apiVersion: policy/v1beta1
            kind: PodDisruptionBudget
            metadata:
               generation: 1
               name: test
               namespace: qual
            status:
               observedGeneration: 1
            """;

    @Test
    void testPDBStatus() {
        runStatusTest("pdbNotObserved", pdbNotObserved, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("pdbObserved", pdbObserved, KubeObjectStatus.CURRENT);
    }


    String crdNoStatus = """
            apiVersion: something/v1
            kind: MyCR
            metadata:
               generation: 1
               name: test
               namespace: qual
            """;

    String crdMismatchStatusGeneration = """
            apiVersion: something/v1
            kind: MyCR
            metadata:
               name: test
               namespace: qual
               generation: 2
            status:
               observedGeneration: 1
            """;

    String crdReady = """
            apiVersion: something/v1
            kind: MyCR
            metadata:
               name: test
               namespace: qual
               generation: 1
            status:
               conditions:
                - type: Ready
                  status: "True"
                  message: All looks ok
                  reason: AllOk
            """;

    String crdNotReady = """
            apiVersion: something/v1
            kind: MyCR
            metadata:
               generation: 1
               name: test
               namespace: qual
            status:
               observedGeneration: 1
               conditions:
                - type: Ready
                  status: "False"
                  reason: NotReadyYet
            """;

    String crdNoCondition = """
            apiVersion: something/v1
            kind: MyCR
            metadata:
               name: test
               namespace: qual
               generation: 1
            status:
               conditions:
                - type: SomeCondition
                  status: "False"
            """;

    @Test
    void testCRDGenericStatus() {
        runStatusTest("crdNoStatus", crdNoStatus, KubeObjectStatus.CURRENT);
        runStatusTest("crdReady", crdReady, KubeObjectStatus.CURRENT);
        runStatusTest("crdNotReady", crdNotReady, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("crdNoCondition", crdNoCondition, KubeObjectStatus.CURRENT);
        runStatusTest("crdMismatchStatusGeneration", crdMismatchStatusGeneration, KubeObjectStatus.IN_PROGRESS);
    }


    String jobNoStatus = """
            apiVersion: batch/v1
            kind: Job
            metadata:
               name: test
               namespace: qual
               generation: 1
            """;

    String jobComplete = """
            apiVersion: batch/v1
            kind: Job
            metadata:
               name: test
               namespace: qual
               generation: 1
            status:
               succeeded: 1
               active: 0
               conditions:
                - type: Complete 
                  status: "True"
            """;

    String jobFailed = """
            apiVersion: batch/v1
            kind: Job
            metadata:
               name: test
               namespace: qual
               generation: 1
            spec:
               completions: 4
            status:
               succeeded: 3
               failed: 1
               conditions:
                - type: Failed 
                  status: "True"
                  reason: JobFailed
            """;

    String jobInProgress = """
            apiVersion: batch/v1
            kind: Job
            metadata:
               name: test
               namespace: qual
               generation: 1
            spec:
               completions: 10
               parallelism: 2
            status:
               startTime: "2019-06-04T01:17:13Z"
               succeeded: 3
               failed: 1
               active: 2
               conditions:
                - type: Failed 
                  status: "False"
                - type: Complete 
                  status: "False"
            """;

    @Test
    void testJobStatus() {
        runStatusTest("jobNoStatus", jobNoStatus, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("jobComplete", jobComplete, KubeObjectStatus.CURRENT);
        runStatusTest("jobFailed", jobFailed, KubeObjectStatus.FAILED);
        runStatusTest("jobInProgress", jobInProgress, KubeObjectStatus.IN_PROGRESS);
    }


    String cronjobNoStatus = """
            apiVersion: batch/v1
            kind: CronJob
            metadata:
               name: test
               namespace: qual
               generation: 1
            """;

    String cronjobWithStatus = """
            apiVersion: batch/v1
            kind: CronJob
            metadata:
               name: test
               namespace: qual
               generation: 1
            status:
            """;


    @Test
    void testCronJobStatus() {
        runStatusTest("cronjobNoStatus", cronjobNoStatus, KubeObjectStatus.CURRENT);
        runStatusTest("cronjobWithStatus", cronjobWithStatus, KubeObjectStatus.CURRENT);
    }


    String serviceDefault = """
            apiVersion: v1
            kind: Service
            metadata:
               name: test
               namespace: qual
               generation: 1
            """;

    String serviceNodePort = """
            apiVersion: v1
            kind: Service
            metadata:
               name: test
               namespace: qual
               generation: 1
            spec:
              type: NodePort
            """;

    String serviceLBok = """
            apiVersion: v1
            kind: Service
            metadata:
               name: test
               namespace: qual
               generation: 1
            spec:
              type: LoadBalancer
              clusterIP: "1.2.3.4"
            """;
    String serviceLBnok = """
            apiVersion: v1
            kind: Service
            metadata:
               name: test
               namespace: qual
               generation: 1
            spec:
              type: LoadBalancer
            """;

    @Test
    void testServiceStatus() {
        runStatusTest("serviceDefault", serviceDefault, KubeObjectStatus.CURRENT);
        runStatusTest("serviceNodePort", serviceNodePort, KubeObjectStatus.CURRENT);
        runStatusTest("serviceLBnok", serviceLBnok, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("serviceLBok", serviceLBok, KubeObjectStatus.CURRENT);
    }


    String crdNoConditions = """
            apiVersion: apiextensions.k8s.io/v1
            kind: CustomResourceDefinition
            metadata:
               generation: 1
            """;

    String crdInstalling = """
            apiVersion: apiextensions.k8s.io/v1
            kind: CustomResourceDefinition
            metadata:
               generation: 1
            status:
               conditions:
                - type: NamesAccepted
                  status: "True"
                  reason: NoConflicts
                - type: Established
                  status: "False"
                  reason: Installing
            """;

    String crdNamesNotAccepted = """
            apiVersion: apiextensions.k8s.io/v1
            kind: CustomResourceDefinition
            metadata:
               generation: 1
            status:
               conditions:
                - type: NamesAccepted
                  status: "False"
                  reason: SomeReason
            """;

    String crdEstablished = """
            apiVersion: apiextensions.k8s.io/v1
            kind: CustomResourceDefinition
            metadata:
               generation: 1
            status:
               conditions:
                - type: NamesAccepted
                  status: "True"
                  reason: NoConflicts
                - type: Established
                  status: "True"
                  reason: InitialNamesAccepted
            """;

    @Test
    void testCRDStatus() {
        runStatusTest("crdNoConditions", crdNoConditions, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("crdInstalling", crdInstalling, KubeObjectStatus.IN_PROGRESS);
        runStatusTest("crdNamesNotAccepted", crdNamesNotAccepted, KubeObjectStatus.FAILED);
        runStatusTest("crdEstablished", crdEstablished, KubeObjectStatus.CURRENT);
    }

    String secretObj= """
            apiVersion: v1
            kind: Secret
            metadata:
               name: test
               namespace: qual
               generation: 1
            """;

    String configMapObj= """
            apiVersion: v1
            kind: ConfigMap
            metadata:
               name: test
               namespace: qual
               generation: 1
            """;
    @Test
    void testAlwaysOnStatus() {
        runStatusTest("secret", secretObj, KubeObjectStatus.CURRENT);
        runStatusTest("configMap", configMapObj, KubeObjectStatus.CURRENT);
    }
}
