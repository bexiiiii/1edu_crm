package com.ondeedu.common.config;

import com.ondeedu.common.security.SystemPermission;
import com.ondeedu.common.tenant.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   TenantContextFilter tenantContextFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/internal/**",
                    "/api/v1/register",
                    "/api/v1/settings/google-drive-backup/oauth/callback",
                    "/api/v1/settings/yandex-disk-backup/oauth/callback"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        http.addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    static class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        @SuppressWarnings("unchecked")
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            // Extract realm roles
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            List<String> realmRolesList = List.of();
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                realmRolesList = (List<String>) realmAccess.get("roles");
            }

            // Extract resource roles (client roles)
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            Stream<String> resourceRoles = Stream.empty();
            if (resourceAccess != null) {
                resourceRoles = resourceAccess.values().stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .filter(map -> map.containsKey("roles"))
                    .flatMap(map -> ((List<String>) map.get("roles")).stream());
            }

            Stream<String> roleAuthorities = Stream.concat(realmRolesList.stream(), resourceRoles)
                    .map(role -> "ROLE_" + role.toUpperCase());

            // SUPER_ADMIN gets ALL system permissions automatically — no need to configure manually
            boolean isSuperAdmin = realmRolesList.stream()
                    .anyMatch(r -> "SUPER_ADMIN".equalsIgnoreCase(r));

            Stream<String> permissionAuthorities;
            if (isSuperAdmin) {
                permissionAuthorities = Arrays.stream(SystemPermission.values())
                        .map(Enum::name);
            } else {
                // Regular users: read permissions from JWT claim (set via Keycloak protocol mapper)
                List<String> permissionsClaim = jwt.getClaimAsStringList("permissions");
                permissionAuthorities = permissionsClaim != null
                        ? permissionsClaim.stream() : Stream.empty();
            }

            return Stream.concat(roleAuthorities, permissionAuthorities)
                    .map(SimpleGrantedAuthority::new)
                    .map(GrantedAuthority.class::cast)
                    .toList();
        }
    }
}