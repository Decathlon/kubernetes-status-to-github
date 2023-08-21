package com.decathlon.github.kubernetesstatus.registration;

import com.decathlon.github.kubernetesstatus.data.properties.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class NativeRegistrationTest {

    @Test
    void shouldRegisterKubeHints() {
        RuntimeHints hints = new RuntimeHints();
        new OfficialKubeRegistration().registerHints(hints, getClass().getClassLoader());
        assertThat(RuntimeHintsPredicates.reflection().onType(io.kubernetes.client.custom.V1Patch.V1PatchAdapter.class)).accepts(hints);
    }

    @Test
    void shouldRegisterK2GHints() {
        RuntimeHints hints = new RuntimeHints();
        new S2GNativeRegistration().registerHints(hints, getClass().getClassLoader());
        assertThat(RuntimeHintsPredicates.reflection().onType(AppProperties.KubernetesProperties.class)).accepts(hints);
    }
}
