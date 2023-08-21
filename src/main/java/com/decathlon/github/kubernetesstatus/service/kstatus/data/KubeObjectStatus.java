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
package com.decathlon.github.kubernetesstatus.service.kstatus.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum KubeObjectStatus {
    @JsonProperty("InProgress") @SerializedName("InProgress") IN_PROGRESS(1,"InProgress"),
    @JsonProperty("Current") @SerializedName("Current") CURRENT(4,"Current"),
    @JsonProperty("Failed") @SerializedName("Failed") FAILED(3,"Failed"),
    @JsonProperty("Terminating") @SerializedName("Terminating") TERMINATING(2,"Terminating"),
    @JsonProperty("Unknown") @SerializedName("Unknown") UNKNOWN(0,"Unknown");

    @JsonIgnore
    private final int stateOrder;
    @JsonIgnore
    private final String pascalCase;

    KubeObjectStatus(int stateOrder, String pascalCase) {
        this.stateOrder = stateOrder;
        this.pascalCase =pascalCase;
    }

    @JsonIgnore
    public boolean canMoveTo(KubeObjectStatus newStatus) {
        return this.stateOrder<newStatus.stateOrder;
    }

    @JsonIgnore
    public String pascalCase() {
        return pascalCase;
    }

    @JsonCreator
    public static KubeObjectStatus forValues(String value) {
        for (KubeObjectStatus kubeObjectStatus : KubeObjectStatus.values()) {
            if (kubeObjectStatus.name().equalsIgnoreCase(value) || kubeObjectStatus.pascalCase().equalsIgnoreCase(value)) {
                return kubeObjectStatus;
            }
        }
        return null;
    }
}
