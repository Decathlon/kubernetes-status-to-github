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

public class Generic {
    private Generic(){
        // no instance
    }

    /**
     * checkGenericProperties looks at the properties that are available on
     * all or most of the Kubernetes resources. If a decision can be made based
     * on this information, there is no need to look at the resource-specidic
     * rules.
     * <p>
     * This also checks for the presence of the conditions defined in this package.
     * If any of these are set on the resource, a decision is made solely based
     * on this and none of the resource specific rules will be used. The goal here
     * is that if controllers, built-in or custom, use these conditions, we can easily
     * find status of resources.
     */
    public static KubeObjectResult checkGenericProperties(DynamicKubernetesObject obj) {
        var deletionTimestamp = Utils.findNode(obj.getRaw(), "metadata","deletionTimestamp");
        if (deletionTimestamp != null && deletionTimestamp.isJsonPrimitive()) {
            return new KubeObjectResult(KubeObjectStatus.TERMINATING, "Resource scheduled for deletion");
        }

        var res=checkGeneration(obj);
        if (res!=null){
            return res;
        }

        // Check if the resource has any of the standard conditions. If so, we just use them
        // and no need to look at anything else.
        var withCondition = Utils.getObjectWithConditions(obj.getRaw());
        if (withCondition == null) {
            return null;
        }
        for (var cond : withCondition.status().conditions()) {
            if ("Reconciling".equals(cond.type())&& cond.status() == Conditions.ConditionStatus.TRUE) {
                return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, cond.message());
            }
            if ("Stalled".equals(cond.type())&& cond.status() == Conditions.ConditionStatus.TRUE) {
                return new KubeObjectResult(KubeObjectStatus.FAILED, cond.message());
            }
        }
        return null;
    }


    private static KubeObjectResult checkGeneration(DynamicKubernetesObject obj) {
        // ensure that the meta generation is observed
        var generationRaw = Utils.findNode(obj.getRaw(), "metadata","generation");
        if (generationRaw==null || !generationRaw.isJsonPrimitive() || !generationRaw.getAsJsonPrimitive().isNumber()){
            return null;
        }

        var observedGenerationRaw = Utils.findNode(obj.getRaw(), "status", "observedGeneration");
        if (observedGenerationRaw==null || !observedGenerationRaw.isJsonPrimitive() || !observedGenerationRaw.getAsJsonPrimitive().isNumber()){
            return null;
        }
        long generation = generationRaw.getAsLong();
        long observedGeneration = observedGenerationRaw.getAsLong();

        // Resource does not have this field, so we can't do this check.
        // TODO(mortent): Verify behavior of not set vs does not exist.
        if (observedGeneration != generation) {
            var msg=String.format("%s generation is %d, but latest observed generation is %d", obj.getKind(), generation, observedGeneration);
            return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, msg);
        }
        return null;
    }
}
