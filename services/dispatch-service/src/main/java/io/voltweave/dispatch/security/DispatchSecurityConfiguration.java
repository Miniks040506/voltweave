package io.voltweave.dispatch.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class DispatchSecurityConfiguration {
    @Bean
    SecurityFilterChain dispatchSecurityFilterChain(
            HttpSecurity http,
            DispatchSecurityProblemWriter problems
    ) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                "/actuator/health", "/actuator/info", "/actuator/prometheus"
                        ).permitAll()
                        .requestMatchers("/internal/**").access((authentication, context) ->
                                new AuthorizationDecision(
                                        authentication.get().getPrincipal() instanceof Jwt jwt
                                                && "voltweave-internal".equals(
                                                        jwt.getClaimAsString("azp")
                                                )
                                ))
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                problems.unauthorized(request, response))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.forbidden(request, response)))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
