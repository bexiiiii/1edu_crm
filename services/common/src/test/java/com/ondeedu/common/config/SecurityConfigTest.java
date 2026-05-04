package com.ondeedu.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void converterAddsDisplayRoleAliasForTenantScopedAdminRole() {
        SecurityConfig.KeycloakRealmRoleConverter converter = new SecurityConfig.KeycloakRealmRoleConverter();
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of(
                        "realm_access", Map.of("roles", List.of("TENANT_demo__ADMIN")),
                        "permissions", List.of("LESSONS_MARK_ATTENDANCE")
                )
        );

        List<String> authorities = converter.convert(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains(
                "ROLE_TENANT_DEMO__ADMIN",
                "ROLE_ADMIN",
                "LESSONS_MARK_ATTENDANCE"
        );
    }
}
