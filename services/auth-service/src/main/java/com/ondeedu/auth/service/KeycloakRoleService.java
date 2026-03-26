package com.ondeedu.auth.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.security.PermissionUtils;
import com.ondeedu.common.security.RoleNameUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakRoleService {

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public void syncRole(String tenantId, String roleName, String description, List<String> permissions) {
        List<String> normalizedPermissions = PermissionUtils.normalizePermissions(permissions);
        String keycloakRoleName = RoleNameUtils.toKeycloakRoleName(tenantId, roleName);
        RoleRepresentation role = findRoleRepresentation(keycloakRoleName);
        if (role == null) {
            RoleRepresentation createRequest = new RoleRepresentation();
            createRequest.setName(keycloakRoleName);
            createRequest.setDescription(description);
            createRequest.setAttributes(buildAttributes(roleName, normalizedPermissions));
            keycloak.realm(realm).roles().create(createRequest);
            log.info("Created Keycloak role {}", keycloakRoleName);
        } else {
            role.setDescription(description);
            role.setAttributes(buildAttributes(roleName, normalizedPermissions));
            keycloak.realm(realm).roles().get(keycloakRoleName).update(role);
            log.info("Updated Keycloak role {}", keycloakRoleName);
        }

        syncRoleMembersPermissions(keycloakRoleName, normalizedPermissions);
    }

    public void deleteRole(String tenantId, String roleName) {
        String normalizedRoleName = RoleNameUtils.normalizeRoleName(roleName);
        if (RoleNameUtils.isSystemRole(normalizedRoleName)) {
            throw new BusinessException("SYSTEM_ROLE_DELETE_FORBIDDEN",
                    "Built-in system roles cannot be deleted");
        }

        String keycloakRoleName = RoleNameUtils.toKeycloakRoleName(tenantId, normalizedRoleName);
        RoleRepresentation role = findRoleRepresentation(keycloakRoleName);
        if (role == null) {
            return;
        }

        RoleResource roleResource = keycloak.realm(realm).roles().get(keycloakRoleName);
        if (!roleResource.getUserMembers(0, 1).isEmpty()) {
            throw new BusinessException("ROLE_IN_USE",
                    "Role '" + normalizedRoleName + "' is assigned to users. Reassign users before deleting it.");
        }

        roleResource.remove();
        log.info("Deleted Keycloak role {}", keycloakRoleName);
    }

    public List<String> getRolePermissions(String keycloakRoleName) {
        RoleRepresentation role = findRoleRepresentation(keycloakRoleName);
        if (role == null || role.getAttributes() == null) {
            return List.of();
        }
        List<String> permissions = role.getAttributes().get(KeycloakUserService.PERMISSIONS_ATTRIBUTE);
        return permissions != null ? PermissionUtils.normalizePermissions(permissions) : List.of();
    }

    private void syncRoleMembersPermissions(String keycloakRoleName, List<String> permissions) {
        RoleResource roleResource = keycloak.realm(realm).roles().get(keycloakRoleName);
        int first = 0;
        int batchSize = 200;

        while (true) {
            List<UserRepresentation> members = roleResource.getUserMembers(first, batchSize);
            if (members == null || members.isEmpty()) {
                return;
            }

            for (UserRepresentation member : members) {
                syncRoleBackedPermissions(member.getId(), keycloakRoleName, permissions);
            }

            first += members.size();
        }
    }

    private void syncRoleBackedPermissions(String userId, String keycloakRoleName, List<String> permissions) {
        UserResource userResource = keycloak.realm(realm).users().get(userId);
        UserRepresentation user = userResource.toRepresentation();

        Map<String, List<String>> attrs = user.getAttributes() != null
                ? new HashMap<>(user.getAttributes()) : new HashMap<>();
        String roleSource = KeycloakUserService.rolePermissionsSource(keycloakRoleName);
        String currentSource = firstAttribute(attrs, KeycloakUserService.PERMISSIONS_SOURCE_ATTRIBUTE);
        if (currentSource != null && !roleSource.equals(currentSource)) {
            return;
        }

        if (permissions == null || permissions.isEmpty()) {
            attrs.remove(KeycloakUserService.PERMISSIONS_ATTRIBUTE);
        } else {
            attrs.put(KeycloakUserService.PERMISSIONS_ATTRIBUTE, permissions);
        }
        attrs.put(KeycloakUserService.PERMISSIONS_SOURCE_ATTRIBUTE, List.of(roleSource));
        user.setAttributes(attrs);
        userResource.update(user);
    }

    private RoleRepresentation findRoleRepresentation(String keycloakRoleName) {
        try {
            return keycloak.realm(realm).roles().get(keycloakRoleName).toRepresentation();
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, List<String>> buildAttributes(String displayName, List<String> permissions) {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("display_name", List.of(RoleNameUtils.normalizeRoleName(displayName)));
        attributes.put(KeycloakUserService.PERMISSIONS_ATTRIBUTE,
                permissions != null ? permissions : List.of());
        return attributes;
    }

    private String firstAttribute(Map<String, List<String>> attrs, String key) {
        List<String> values = attrs.get(key);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }
}
