package com.decathlon.github.kubernetesstatus.registration;

import com.google.gson.annotations.JsonAdapter;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
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
        var reflections = new Reflections("io.kubernetes", Scanners.SubTypes.filterResultsBy(x->true), Scanners.TypesAnnotated);

        var all = new HashSet<Class<?>>();
        all.addAll(reflections.getSubTypesOf(Object.class));
        all.addAll(findJsonAdapters(reflections));
        all.forEach(clzz -> hints.reflection().registerType(clzz, MemberCategory.values()));

        Stream.of(
                okhttp3.internal.http.RetryAndFollowUpInterceptor.Companion.class,
                io.kubernetes.client.informer.cache.ProcessorListener.class,
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
