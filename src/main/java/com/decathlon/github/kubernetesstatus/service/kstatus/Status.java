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
package com.decathlon.github.kubernetesstatus.service.kstatus;

import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectResult;
import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectStatus;
import com.decathlon.github.kubernetesstatus.service.kstatus.internal.Conditions;
import com.decathlon.github.kubernetesstatus.service.kstatus.internal.Core;
import com.decathlon.github.kubernetesstatus.service.kstatus.internal.Generic;
import com.decathlon.github.kubernetesstatus.service.kstatus.internal.Utils;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;

import java.util.Collections;

public class Status {
    private Status(){
        // no instance
    }

    /**
     * Compute finds the status of a given unstructured resource. It does not
     * fetch the state of the resource from a cluster, so the provided unstructured
     * must have the complete state, including status.
     * <p>
     * The result also contains a message that provides more information on why
     * the resource has the given status.
     * Finally, the result also contains
     * a list of standard resources that would belong on the given resource.
     *
     * @param obj the unstructured resource to compute the status for
     * @return The returned result contains the status of the resource and a message
     */
    public static KubeObjectResult compute(DynamicKubernetesObject obj) {
        var res= Generic.checkGenericProperties(obj);
        // If res is not nil, it means the generic checks was able to determine
        // the status of the resource. We don't need to check the type-specific
        // rules.
        if (res!=null){
            return res;
        }

        res=Core.getLegacyConditions(obj);
        if (res!=null){
            return res;
        }

        // If neither the generic properties of the resource-specific rules
        // can determine status, we do one last check to see if the resource
        // does expose a Ready condition. Ready conditions do not adhere
        // to the Kubernetes design recommendations, but they are pretty widely
        // used.
        res = checkReadyCondition(obj);
        if (res != null){
            return res;
        }

        // The resource is not one of the built-in types with specific
        // rules and we were unable to make a decision based on the
        // generic rules. In this case we assume that the absence of any known
        // conditions means the resource is current.
        return new KubeObjectResult(KubeObjectStatus.CURRENT, "Resource is current");
    }



    /**
     * checkReadyCondition checks if a resource has a Ready condition, and
     * if so, it will use the value of this condition to determine the status.
     * <p>
     * There are a few challenges with this:
     * - If a resource doesn't set the Ready condition until it is True,
     * the library have no way of telling whether the resource is using the
     * Ready condition, so it will fall back to the strategy for unknown
     * resources, which is to assume they are always reconciled.
     * - If the library sees the resource before the controller has had
     * a chance to update the conditions, it also will not realize the
     * resource use the Ready condition.
     * - There is no way to determine if a resource with the Ready condition
     * set to False is making progress or is doomed.
     *
     * @param obj the unstructured resource to compute the status for
     * @return The returned result contains the status of the resource and a message
     */
    private static KubeObjectResult checkReadyCondition(DynamicKubernetesObject obj) {
        var withCondition = Utils.getObjectWithConditions(obj.getRaw());
        var conds = withCondition!=null?withCondition.status().conditions(): Collections.<Conditions.BasicCondition>emptyList();

        for (var cond : conds) {
            if (!"Ready".equals(cond.type())) {
                continue;
            }
            switch (cond.status()) {
                case TRUE:
                    return new KubeObjectResult(KubeObjectStatus.CURRENT, "Resource is Ready");
                case FALSE, UNKNOWN:
                    // For now we just treat an unknown condition value as
                    // InProgress. We should consider if there are better ways
                    // to handle it.
                    return new KubeObjectResult(KubeObjectStatus.IN_PROGRESS, cond.message());
                default:
                    // Do nothing in this case.
            }
        }
        return null;
    }
}
