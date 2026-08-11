package io.voltweave.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTests {
    @Test
    void convertsRealmRolesToSpringAuthorities() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin-subject")
                .claim("realm_access", Map.of("roles", List.of("ADMIN", "CUSTOMER")))
                .build();

        assertThat(new KeycloakRealmRoleConverter().convert(jwt))
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_CUSTOMER");
    }
}
