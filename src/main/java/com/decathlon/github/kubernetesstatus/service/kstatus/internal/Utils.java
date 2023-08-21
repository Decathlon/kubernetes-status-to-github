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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Utils {
    private Utils() {
        // no instance
    }

    /**
     * GetObjectWithConditions return typed object
     */
    public static Conditions.ObjWithConditions getObjectWithConditions(JsonObject jsonObject) {
        // Manually build to prevent record errors (Gson do not support them correctly
        // (and native do not need to scan the records ;) )
        var conds = findNode(jsonObject, "status", "conditions");
        if (conds == null || !conds.isJsonArray()) {
            return null;
        }
        var lst = new ArrayList<Conditions.BasicCondition>();

        for (var c : conds.getAsJsonArray()) {
            if (!c.isJsonObject()) {
                continue;
            }
            var o = c.getAsJsonObject();
            var type = o.has("type") ? o.get("type").getAsString() : null;
            var statusStr = o.has("status") ? o.get("status").getAsString() : null;
            var reason = o.has("reason") ? o.get("reason").getAsString() : null;
            var message = o.has("message") ? o.get("message").getAsString() : null;
            Conditions.ConditionStatus status = Conditions.ConditionStatus.getConditionStatus(statusStr);

            lst.add(new Conditions.BasicCondition(type, status, reason, message));
        }

        return new Conditions.ObjWithConditions(new Conditions.Status(lst));
    }

    public static boolean hasConditionWithStatus(List<Conditions.BasicCondition> conditions, String conditionType, Conditions.ConditionStatus status) {
        var found = getConditionWithStatus(conditions, conditionType, status);
        return found.isPresent();
    }

    public static Optional<Conditions.BasicCondition> getConditionWithStatus(List<Conditions.BasicCondition> conditions, String conditionType, Conditions.ConditionStatus status) {
        for (var c : conditions) {
            if (Objects.equals(c.type(), conditionType) && (c.status() == status)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /**
     * find node
     */
    public static JsonElement findNode(JsonObject jsonObject, String... attributes) {
        JsonElement current = jsonObject;
        for (String attribute : attributes) {
            if (current.isJsonObject() && current.getAsJsonObject().has(attribute)) {
                current = current.getAsJsonObject().get(attribute);
            } else {
                return null;
            }
        }
        return current;
    }


    /**
     * Return String value for node
     */
    public static String findStringOrDefault(JsonObject jsonObject, String defaultValue, String... attributes) {
        var ret = findNode(jsonObject, attributes);
        if (ret == null || !ret.isJsonPrimitive()) {
            return defaultValue;
        }
        return ret.getAsString();
    }

    /**
     * Return int value for node
     */
    public static int findIntOrDefault(JsonObject jsonObject, int defaultValue, String... attributes) {
        var ret = findNode(jsonObject, attributes);
        if (ret == null || !ret.isJsonPrimitive() || !ret.getAsJsonPrimitive().isNumber()) {
            return defaultValue;
        }
        var n = ret.getAsJsonPrimitive().getAsNumber();

        if (n.intValue() * 1.0 == n.floatValue()) {
            return n.intValue();
        }

        return defaultValue;
    }
}
