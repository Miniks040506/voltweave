package io.voltweave.intelligence.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTests {
    @Test
    void convertsRealmRolesToMethodAuthorities() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("operator")
                .claim("realm_access", Map.of(
                        "roles", List.of("VPP_OPERATOR", "ADMIN")
                ))
                .build();

        assertEquals(
                List.of("ROLE_VPP_OPERATOR", "ROLE_ADMIN"),
                new KeycloakRealmRoleConverter().convert(jwt).stream()
                        .map(authority -> authority.getAuthority())
                        .toList()
        );
    }
}
