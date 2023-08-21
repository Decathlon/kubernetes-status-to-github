package com.decathlon.github.kubernetesstatus.registration;

import io.swagger.annotations.ApiModel;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.decathlon.github.kubernetesstatus.model.GithubDeploymentRef.CURRENT_VERSION;

/**
 * Native registration for bean from our app and some beans there and there.
 */
@Slf4j
public class S2GNativeRegistration implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {

        // We have to declare the model
        registerModel(hints);

        // Those hints are needed for the event recorder part
        // As those class are protected, we have to use reflexion to get them.
        Stream.of(
                "com.github.benmanes.caffeine.cache.SSMS",
                "com.github.benmanes.caffeine.cache.SSMSW",
                "com.github.benmanes.caffeine.cache.PSMS",
                "com.github.benmanes.caffeine.cache.PSWMS",
                "com.github.benmanes.caffeine.cache.PS",
                "com.github.benmanes.caffeine.cache.PSW",
                "com.github.benmanes.caffeine.cache.StripedBuffer"
        ).map(this::resolveClass)
                .filter(Objects::nonNull)
                .forEach(clzz -> hints.reflection().registerType(clzz, MemberCategory.values()));


        // Register data package
        registerPackage(hints, "com.decathlon.github.kubernetesstatus.data.io");
        registerPackage(hints, "com.decathlon.github.kubernetesstatus.data.properties");
    }

    private static void registerPackage(RuntimeHints hints, String packageName) {
        Reflections reflections = new Reflections(packageName, Scanners.SubTypes.filterResultsBy(x->true));
        Set<Class<?>> allDataClasses = reflections.getSubTypesOf(Object.class);
        log.info("Found {} types in package {}", allDataClasses.size(), packageName);
        allDataClasses.forEach(clzz -> {
            log.info("Adding in native registry class {}", clzz.getName());
            hints.reflection().registerType(clzz, MemberCategory.values());
        });
    }

    private Class<?> resolveClass(String x) {
        try {
            return Class.forName(x);
        }catch(Exception e){
            log.error("Registration class not found: {} !!!! ", x, e);
            return null;
        }
    }

    private void registerModel(RuntimeHints hints) {
        var packageName="com.decathlon.github.kubernetesstatus.model."+CURRENT_VERSION;
        Reflections reflections = new Reflections(packageName);
        Set<Class<?>> models = reflections.getTypesAnnotatedWith(ApiModel.class);
        log.info("Will add in native registry {} classes", models.size());
        models.forEach(clzz -> {
            log.info("Adding in native registry class {}", clzz.getName());

            hints.reflection().registerType(clzz, MemberCategory.values());
        });
    }

}
