package com.decathlon.github.kubernetesstatus.registration;

import com.google.gson.annotations.JsonAdapter;
import io.swagger.annotations.ApiModel;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Register major bean from official kubernetes client.
 *  This is inspired by <a href="https://github.com/bootiful-spring-graalvm/hints/blob/main/src/main/java/com/joshlong/kubernetes/official/OfficialNativeConfiguration.java">Spring hints</a>
 */
@Slf4j
public class OfficialKubeRegistration implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
        var reflections = new Reflections("io.kubernetes");
        var apiModels = reflections.getTypesAnnotatedWith(ApiModel.class);
        var jsonAdapters = findJsonAdapters(reflections);

        var all = new HashSet<Class<?>>();
        all.addAll(jsonAdapters);
        all.addAll(apiModels);
        all.forEach(clzz -> hints.reflection().registerType(clzz, MemberCategory.values()));

        Stream.of(
                io.kubernetes.client.informer.cache.ProcessorListener.class,
                io.kubernetes.client.extended.controller.Controller.class,
                io.kubernetes.client.extended.controller.ControllerManager.class,
                io.kubernetes.client.util.generic.GenericKubernetesApi.StatusPatch.class,
                io.kubernetes.client.util.Watch.Response.class
        ).forEach(clzz -> hints.reflection().registerType(clzz, MemberCategory.values()));
    }

    private Set<Class<?>> findJsonAdapters(Reflections reflections) {
        var jsonAdapterClass = JsonAdapter.class;
        return reflections.getTypesAnnotatedWith(jsonAdapterClass).stream().flatMap(clazz -> {
            var list = new HashSet<Class<?>>();
            var annotation = clazz.getAnnotation(jsonAdapterClass);
            if (null != annotation) {
                list.add(annotation.value());
            }
            list.add(clazz);
            list.forEach(c -> log.info("found @JsonAdapter type: " + c.getName()));
            return list.stream();
        }).collect(Collectors.toSet());
    }

}
