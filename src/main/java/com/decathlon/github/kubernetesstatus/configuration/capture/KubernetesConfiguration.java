package com.decathlon.github.kubernetesstatus.configuration.capture;

import com.decathlon.github.kubernetesstatus.data.properties.AppMode;
import com.decathlon.github.kubernetesstatus.data.properties.AppProperties;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.event.EventType;
import io.kubernetes.client.extended.event.legacy.EventBroadcaster;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.extended.event.legacy.LegacyEventBroadcaster;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1EventSource;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Map;

@Configuration(proxyBeanMethods=false)
public class KubernetesConfiguration {
    @Bean
    public ApiClient createApiClient() throws IOException {
        ApiClient client = Config.defaultClient();
        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client);
        return client;
    }

    @Bean
    public EventRecorder eventRecorder(AppProperties appProperties) {
        // Native Conditional is not resolved at runtime wihout big config change.
        if (appProperties.getMode() == AppMode.PROCESS) {
            return new EventRecorder() {
                @Override
                public void event(KubernetesObject object, EventType t, String reason, String format, String... args) {
                    // Do nothing
                }

                @Override
                public void event(KubernetesObject object, Map<String, String> attachedAnnotation, EventType t, String reason, String format, String... args) {
                    // Do nothing
                }

                @Override
                public void event(V1ObjectReference ref, EventType t, String reason, String format, String... args) {
                    // Do nothing
                }

                @Override
                public void event(V1ObjectReference ref, Map<String, String> attachedAnnotation, EventType t, String reason, String format, String... args) {
                    // Do nothing
                }
            };
        }
        EventBroadcaster eventBroadcaster = new LegacyEventBroadcaster(new CoreV1Api());
        eventBroadcaster.startRecording();

        return eventBroadcaster.newRecorder(new V1EventSource().component("ghd-controler"));
    }
}
