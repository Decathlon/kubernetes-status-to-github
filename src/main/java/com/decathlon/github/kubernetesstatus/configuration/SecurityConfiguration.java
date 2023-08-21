package com.decathlon.github.kubernetesstatus.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;


@Configuration(proxyBeanMethods=false)
@EnableWebSecurity
@EnableMethodSecurity(   // If you want to use annotation to secure something [Controller, service, ...]
        // you can remove it if not needed or enable only what you want
        prePostEnabled = true,  // Enable @PreAuthorize / @PostAuthorize ex: \@PreAuthorize("hasRole('ROLE_ADMIN')")
        securedEnabled = true, // Enable @Secured annotation for instance @Secured("ROLE_ADMIN")
        jsr250Enabled = true)  // Enable @RolesAllowed ex: @RolesAllowed("ROLE_ADMIN")
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(matcher -> {
                    matcher.requestMatchers(antMatcher("/actuator/**")).permitAll()
                            .anyRequest().fullyAuthenticated();
                })

                .csrf(AbstractHttpConfigurer::disable) // Only because we are in API
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(config -> config.jwt(c2 -> {
                }))
                .build();
    }
}
