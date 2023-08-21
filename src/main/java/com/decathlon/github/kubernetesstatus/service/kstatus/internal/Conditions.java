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

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

public class Conditions {
    /**
     * ObjWithConditions Represent meta object with status.condition array
     */
    public record ObjWithConditions(
            // Status as expected to be present in most compliant kubernetes resources
            Status status
    ){ }

    /**
     * Status represents the current status of a kubernetes resource
     */
    public record Status (
            // Array of Conditions as expected to be present in kubernetes resources
            List<BasicCondition> conditions
    ){}

    /**
     * BasicCondition fields that are expected in a condition
     */
    public record BasicCondition(
            // Type Condition type
            String type,
            // Status is one of True,False,Unknown
            ConditionStatus status,
            // Reason simple single word reason in CamleCase
            // +optional
            String reason,
            String message
    ){}

    public enum ConditionStatus {
        @SerializedName("True") TRUE("True"),
        @SerializedName("False") FALSE("False"),
        @SerializedName("Unknown") UNKNOWN("Unknown");

        private final String pascalCase;

        ConditionStatus(String pascalCase) {
            this.pascalCase = pascalCase;
        }

        public String pascalCase() {
            return pascalCase;
        }

        public static Conditions.ConditionStatus getConditionStatus(String statusStr) {
            var status = Conditions.ConditionStatus.UNKNOWN;
            for (var s : Conditions.ConditionStatus.values()) {
                if (Objects.equals(s.pascalCase(), statusStr)) {
                    status = s;
                    break;
                }
            }
            return status;
        }
    }

}
