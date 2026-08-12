package io.voltweave.telemetry.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class TelemetrySecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TelemetrySecurityProblemWriter problemWriter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problemWriter)
                        .accessDeniedHandler(problemWriter)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(problemWriter)
                        .accessDeniedHandler(problemWriter)
                        .jwt(jwt -> {}))
                .build();
    }
}
