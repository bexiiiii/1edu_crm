package com.ondeedu.auth.service;

import com.ondeedu.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KeycloakUserServiceTest {

    @Mock
    private Keycloak keycloak;
    @Mock
    private KeycloakRoleService keycloakRoleService;
    @Mock
    private RealmResource realmResource;
    @Mock
    private UsersResource usersResource;
    @Mock
    private UserResource userResource1;
    @Mock
    private UserResource userResource3;
    @Mock
    private RoleMappingResource roleMappingResource1;
    @Mock
    private RoleMappingResource roleMappingResource3;
    @Mock
    private RoleScopeResource realmLevelRoles1;
    @Mock
    private RoleScopeResource realmLevelRoles3;

    private KeycloakUserService service;

    @BeforeEach
    void setUp() {
        service = new KeycloakUserService(keycloak, keycloakRoleService, "ondeedu", "1edu-web-app");

        when(keycloak.realm("ondeedu")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        when(usersResource.get("u1")).thenReturn(userResource1);
        when(userResource1.roles()).thenReturn(roleMappingResource1);
        when(roleMappingResource1.realmLevel()).thenReturn(realmLevelRoles1);
        when(realmLevelRoles1.listEffective()).thenReturn(List.of());

        when(usersResource.get("u3")).thenReturn(userResource3);
        when(userResource3.roles()).thenReturn(roleMappingResource3);
        when(roleMappingResource3.realmLevel()).thenReturn(realmLevelRoles3);
        when(realmLevelRoles3.listEffective()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listUsersFiltersToCurrentTenant() {
        setTenantAuthentication("tenant-a");

        when(usersResource.search(eq(""), eq(0), anyInt())).thenReturn(List.of(
                user("u1", "alpha@example.com", "tenant-a"),
                user("u2", "beta@example.com", "tenant-b"),
                user("u3", "gamma@example.com", "tenant-a")
        ));

        var users = service.listUsers("", 0, 20);

        assertThat(users).extracting("id").containsExactly("u1", "u3");
        assertThat(users).extracting("email").containsExactly("alpha@example.com", "gamma@example.com");
    }

    @Test
    void getUserRejectsCrossTenantAccess() {
        setTenantAuthentication("tenant-a");

        UserRepresentation foreignUser = user("u2", "beta@example.com", "tenant-b");
        UserResource foreignUserResource = org.mockito.Mockito.mock(UserResource.class);
        when(usersResource.get("u2")).thenReturn(foreignUserResource);
        when(foreignUserResource.toRepresentation()).thenReturn(foreignUser);

        assertThatThrownBy(() -> service.getUser("u2"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User");
    }

    @Test
    void resolveTenantIdPrefersAuthenticatedTenant() {
        setTenantAuthentication("tenant-a");

        String resolved = ReflectionTestUtils.invokeMethod(service, "resolveTenantId", "tenant-b");

        assertThat(resolved).isEqualTo("tenant-a");
    }

    private void setTenantAuthentication(String tenantId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .claim("tenant_id", tenantId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));
    }

    private UserRepresentation user(String id, String email, String tenantId) {
        UserRepresentation user = new UserRepresentation();
        user.setId(id);
        user.setUsername(email);
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setAttributes(Map.of("tenant_id", List.of(tenantId)));
        return user;
    }
}
