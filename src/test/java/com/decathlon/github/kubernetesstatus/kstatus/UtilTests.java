package com.decathlon.github.kubernetesstatus.kstatus;

import com.decathlon.github.kubernetesstatus.service.kstatus.internal.Conditions;
import com.decathlon.github.kubernetesstatus.service.kstatus.internal.Utils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UtilTests {

    private static final JsonObject testObj = new Gson().fromJson(
            """
                    {
                        "f1": {
                            "f2": {
                                "i32":   32,
                                "i64":   64,
                                "float": 64.02,
                                "ms": [
                                    {"f1f2ms0f1": 22},
                                    {"f1f2ms1f1": "index1"},
                                ],
                                "msbad": [
                                    {"f1f2ms0f1": 22},
                                    32
                                ]
                            }
                        },
                            
                        "ride": "dragon",
                            
                        "status": {
                            "conditions": [
                                {"f1f2ms0f1": 22},
                                {"f1f2ms1f1": "index1"}
                            ]
                        }
                    }
                    """, JsonObject.class);

    @Test
    void testGetIntField() {
        var v = Utils.findIntOrDefault(testObj, -1, "f1", "f2", "i32");
        assertThat(v).isEqualTo(32);

        v = Utils.findIntOrDefault(testObj, -1, "f1", "f2", "wrongname");
        assertThat(v).isEqualTo(-1);

        v = Utils.findIntOrDefault(testObj, -1, "f1", "f2", "i64");
        assertThat(v).isEqualTo(64);

        v = Utils.findIntOrDefault(testObj, -1, "f1", "f2", "float");
        assertThat(v).isEqualTo(-1);
    }

    @Test
    void testGetStringField() {
        var v = Utils.findStringOrDefault(testObj, "horse", "ride");
        assertThat(v).isEqualTo("dragon");

        v = Utils.findStringOrDefault(testObj, "north", "destination");
        assertThat(v).isEqualTo("north");
    }

    @Test
    void testGetConditions() {
        // Invalid object
        JsonObject json = new Gson().fromJson("""
                {"status": "test"}
                """, JsonObject.class);
        assertThat(Utils.getObjectWithConditions(json)).isNull();

        // Valid object with no status
        json = new Gson().fromJson("""
                {}
                """, JsonObject.class);
        assertThat(Utils.getObjectWithConditions(json)).isNull();

        // Valid object with empty status
        json = new Gson().fromJson("""
                {"status": {}}
                """, JsonObject.class);
        assertThat(Utils.getObjectWithConditions(json)).isNull();

        // Valid object with invalid condition
        json = new Gson().fromJson("""
                {"status": {"conditions": {}}}
                """, JsonObject.class);
        assertThat(Utils.getObjectWithConditions(json)).isNull();

        // Valid object with valid empty
        json = new Gson().fromJson("""
                {"status": {"conditions": []}}
                """, JsonObject.class);
        var obj = Utils.getObjectWithConditions(json);
        assertThat(obj).isNotNull();
        assertThat(obj.status().conditions()).isEmpty();

        // Valid object with correct status
        json = new Gson().fromJson("""
                {"status":{"conditions":[{"type":"type","status":"True","reason":"reason","message":"message"}]}}
                """, JsonObject.class);
        obj = Utils.getObjectWithConditions(json);
        assertThat(obj).isNotNull();
        assertThat(obj.status().conditions()).hasSize(1);
        assertThat(obj.status().conditions().get(0)).isEqualTo(new Conditions.BasicCondition("type", Conditions.ConditionStatus.TRUE, "reason", "message"));

        // Valid object with condition limited
        json = new Gson().fromJson("""
                {"status":{"conditions":[{"t":"type","s":"True","r":"reason","m":"message"}]}}
                """, JsonObject.class);
        obj = Utils.getObjectWithConditions(json);
        assertThat(obj).isNotNull();
        assertThat(obj.status().conditions()).hasSize(1);
        assertThat(obj.status().conditions().get(0)).isEqualTo(new Conditions.BasicCondition(null, Conditions.ConditionStatus.UNKNOWN, null, null));


        // Valid object with condition limited
        json = new Gson().fromJson("""
                {"status":{"conditions":[
                    {"t":"type","s":"True","r":"reason","m":"message"},
                    {"type":"type","status":"True","reason":"reason","message":"message"}
                    ]}}
                """, JsonObject.class);
        obj = Utils.getObjectWithConditions(json);
        assertThat(obj).isNotNull();
        assertThat(obj.status().conditions()).hasSize(2);
        assertThat(obj.status().conditions()).contains(new Conditions.BasicCondition(null, Conditions.ConditionStatus.UNKNOWN, null, null));
        assertThat(obj.status().conditions()).contains(new Conditions.BasicCondition("type", Conditions.ConditionStatus.TRUE, "reason", "message"));
    }
}
