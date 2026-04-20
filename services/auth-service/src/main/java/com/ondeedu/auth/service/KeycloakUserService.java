package com.ondeedu.auth.service;

import com.ondeedu.auth.dto.ChangeOwnPasswordRequest;
import com.ondeedu.auth.dto.ChangePasswordRequest;
import com.ondeedu.auth.dto.CreateUserRequest;
import com.ondeedu.auth.dto.UpdateProfileRequest;
import com.ondeedu.auth.dto.UpdateUserRequest;
import com.ondeedu.auth.dto.UserDto;
import com.ondeedu.common.audit.AuditAction;
import com.ondeedu.common.audit.AuditLogPublisher;
import com.ondeedu.common.audit.TenantAuditEvent;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.security.DefaultRolePermissions;
import com.ondeedu.common.security.PermissionUtils;
import com.ondeedu.common.security.RoleNameUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KeycloakUserService {

    static final String PERMISSIONS_ATTRIBUTE = "permissions";
    static final String PERMISSIONS_SOURCE_ATTRIBUTE = "permissions_source";
    static final String BRANCH_IDS_ATTRIBUTE = "branch_ids";
    private static final String USER_PERMISSIONS_SOURCE = "USER";

    private final Keycloak keycloak;
    private final KeycloakRoleService keycloakRoleService;
    private final AuditLogPublisher auditLogPublisher;
    private final String realm;
    private final String frontendClientId;

    @Value("${keycloak.server-url}")
    private String serverUrl;

    public KeycloakUserService(Keycloak keycloak,
                               KeycloakRoleService keycloakRoleService,
                               AuditLogPublisher auditLogPublisher,
                               @Value("${keycloak.realm}") String realm,
                               @Value("${keycloak.frontend-client-id:1edu-web-app}") String frontendClientId) {
        this.keycloak = keycloak;
        this.keycloakRoleService = keycloakRoleService;
        this.auditLogPublisher = auditLogPublisher;
        this.realm = realm;
        this.frontendClientId = frontendClientId;
    }

    public UserDto createUser(CreateUserRequest request) {
        String tenantId = resolveTenantId(request.getTenantId());
        List<String> branchIds = resolveAndValidateRequestedBranchIds(request.getBranchIds());
        String keycloakRoleName = resolveKeycloakRoleName(tenantId, request.getRole());
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEnabled(true);
        user.setEmailVerified(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getPassword());
        credential.setTemporary(false);
        user.setCredentials(Collections.singletonList(credential));
        user.setAttributes(buildUserAttributes(tenantId, request.getStaffId(), branchIds));

        try (Response response = keycloak.realm(realm).users().create(user)) {
            if (response.getStatus() == 409) {
                throw new BusinessException("DUPLICATE_USER",
                    "User with username '" + request.getUsername() + "' or email already exists");
            }
            if (response.getStatus() != 201) {
                throw new BusinessException("USER_CREATION_FAILED",
                    "Failed to create user in Keycloak. Status: " + response.getStatus());
            }

            // Extract user ID from Location header: .../users/{id}
            String location = response.getHeaderString("Location");
            String userId = location.substring(location.lastIndexOf('/') + 1);

            log.info("Created Keycloak user: {} with id: {}", request.getUsername(), userId);

            // Re-apply attributes via update call for reliability with Keycloak user profile validations.
            UserResource userResource = keycloak.realm(realm).users().get(userId);
            UserRepresentation createdUser = userResource.toRepresentation();
            Map<String, List<String>> attrs = createdUser.getAttributes() != null
                    ? new HashMap<>(createdUser.getAttributes()) : new HashMap<>();
            mergeUserAttributes(attrs, tenantId, request.getStaffId(), branchIds);
            createdUser.setAttributes(attrs);
            userResource.update(createdUser);
            log.info("Applied tenant/staff/branch attributes for user: {}", userId);

            // Assign realm role
            assignRole(userId, keycloakRoleName);

            applyRoleAndPermissions(userId, keycloakRoleName, request.getPermissions());

            auditLogPublisher.publishTenant(TenantAuditEvent.builder()
                    .tenantId(tenantId)
                    .action(AuditAction.USER_CREATED)
                    .category("USERS")
                    .actorId(currentTenantId())
                    .targetType("USER")
                    .targetId(userId)
                    .targetName(request.getFirstName() + " " + request.getLastName())
                    .build());

            return getUser(userId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating user {}: {}", request.getUsername(), e.getMessage(), e);
            throw new BusinessException("USER_CREATION_FAILED", "Failed to create user: " + e.getMessage());
        }
    }

    public UserDto getUser(String userId) {
        try {
            UserRepresentation user = keycloak.realm(realm).users().get(userId).toRepresentation();
            if (user == null) {
                throw new ResourceNotFoundException("User", "id", userId);
            }
            assertCurrentTenantAccess(userId, user);
            return toDto(userId, user);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching user {}: {}", userId, e.getMessage(), e);
            throw new BusinessException("USER_FETCH_FAILED", "Failed to fetch user: " + e.getMessage());
        }
    }

    public UserDto updateUser(String userId, UpdateUserRequest request) {
        try {
            UserResource userResource = keycloak.realm(realm).users().get(userId);
            UserRepresentation user = userResource.toRepresentation();
            if (user == null) {
                throw new ResourceNotFoundException("User", "id", userId);
            }
            assertCurrentTenantAccess(userId, user);

            if (request.getEmail() != null) {
                user.setEmail(request.getEmail());
            }
            if (request.getFirstName() != null) {
                user.setFirstName(request.getFirstName());
            }
            if (request.getLastName() != null) {
                user.setLastName(request.getLastName());
            }
            if (request.getStaffId() != null || request.getBranchIds() != null) {
                Map<String, List<String>> attrs = user.getAttributes() != null
                        ? new HashMap<>(user.getAttributes()) : new HashMap<>();

                if (request.getStaffId() != null) {
                    attrs.put("staff_id", List.of(request.getStaffId().toString()));
                }

                if (request.getBranchIds() != null) {
                    List<String> branchIds = resolveAndValidateRequestedBranchIds(request.getBranchIds());
                    if (branchIds.isEmpty()) {
                        attrs.remove(BRANCH_IDS_ATTRIBUTE);
                    } else {
                        attrs.put(BRANCH_IDS_ATTRIBUTE, branchIds);
                    }
                }

                user.setAttributes(attrs);
            }

            userResource.update(user);
            log.info("Updated Keycloak user: {}", userId);
            auditLogPublisher.publishTenant(TenantAuditEvent.builder()
                    .tenantId(currentTenantId())
                    .action(AuditAction.USER_UPDATED)
                    .category("USERS")
                    .actorId(currentTenantId())
                    .targetType("USER")
                    .targetId(userId)
                    .build());

            if (request.getRole() != null) {
                String tenantId = resolveTenantIdForExistingUser(user);
                String keycloakRoleName = resolveKeycloakRoleName(tenantId, request.getRole());
                // Remove all existing realm roles and assign the new one
                removeAllRealmRoles(userId);
                assignRole(userId, keycloakRoleName);
                applyRoleAndPermissions(userId, keycloakRoleName, request.getPermissions());
            } else if (request.getPermissions() != null) {
                storePermissionsAttribute(userId, PermissionUtils.normalizePermissions(request.getPermissions()), USER_PERMISSIONS_SOURCE);
            }

            return getUser(userId);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating user {}: {}", userId, e.getMessage(), e);
            throw new BusinessException("USER_UPDATE_FAILED", "Failed to update user: " + e.getMessage());
        }
    }

    public void deleteUser(String userId) {
        try {
            UserResource userResource = keycloak.realm(realm).users().get(userId);
            UserRepresentation user = userResource.toRepresentation();
            if (user == null) {
                throw new ResourceNotFoundException("User", "id", userId);
            }
            assertCurrentTenantAccess(userId, user);

            // Soft delete: disable the user instead of permanently removing
            user.setEnabled(false);
            userResource.update(user);
            log.info("Disabled Keycloak user: {}", userId);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error disabling user {}: {}", userId, e.getMessage(), e);
            throw new BusinessException("USER_DELETE_FAILED", "Failed to disable user: " + e.getMessage());
        }
    }

    public void resetPassword(String userId, ChangePasswordRequest request) {
        try {
            UserRepresentation user = keycloak.realm(realm).users().get(userId).toRepresentation();
            if (user == null) {
                throw new ResourceNotFoundException("User", "id", userId);
            }
            assertCurrentTenantAccess(userId, user);

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(request.getNewPassword());
            credential.setTemporary(false);

            keycloak.realm(realm).users().get(userId).resetPassword(credential);
            log.info("Reset password for Keycloak user: {}", userId);
        } catch (Exception e) {
            log.error("Error resetting password for user {}: {}", userId, e.getMessage(), e);
            throw new BusinessException("PASSWORD_RESET_FAILED", "Failed to reset password: " + e.getMessage());
        }
    }

    public List<UserDto> listUsers(String search, int page, int size) {
        try {
            int safePage = Math.max(page, 0);
            int safeSize = Math.max(size, 1);
            String tenantId = currentTenantId();

            if (!StringUtils.hasText(tenantId)) {
                List<UserRepresentation> users = keycloak.realm(realm).users()
                    .search(search, safePage * safeSize, safeSize);
                return users.stream()
                    .map(u -> toDto(u.getId(), u))
                    .collect(Collectors.toList());
            }

            int remainingToSkip = safePage * safeSize;
            int first = 0;
            int batchSize = Math.max(safeSize * 4, 100);
            List<UserDto> result = new ArrayList<>(safeSize);

            while (result.size() < safeSize) {
                List<UserRepresentation> batch = keycloak.realm(realm).users().search(search, first, batchSize);
                if (batch.isEmpty()) {
                    break;
                }

                for (UserRepresentation user : batch) {
                    if (!belongsToTenant(user, tenantId)) {
                        continue;
                    }
                    if (remainingToSkip > 0) {
                        remainingToSkip--;
                        continue;
                    }

                    result.add(toDto(user.getId(), user));
                    if (result.size() >= safeSize) {
                        break;
                    }
                }

                if (batch.size() < batchSize) {
                    break;
                }
                first += batch.size();
            }

            return result;
        } catch (Exception e) {
            log.error("Error listing users: {}", e.getMessage(), e);
            throw new BusinessException("USER_LIST_FAILED", "Failed to list users: " + e.getMessage());
        }
    }

    public String resolveCurrentUserId(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException("USER_ID_MISSING",
                    "Current access token is missing. Please sign in again.");
        }

        if (StringUtils.hasText(jwt.getSubject())) {
            return jwt.getSubject();
        }

        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (StringUtils.hasText(preferredUsername)) {
            String userId = findUserIdByUsername(preferredUsername);
            if (userId != null) {
                return userId;
            }
        }

        String email = jwt.getClaimAsString("email");
        if (StringUtils.hasText(email)) {
            String userId = findUserIdByEmail(email);
            if (userId != null) {
                return userId;
            }
        }

        throw new BusinessException("USER_ID_MISSING",
                "Current access token does not contain a usable user identifier. Please sign in again.");
    }

    public UserDto getProfile(String userId) {
        return getUser(userId);
    }

    public UserDto updateProfile(String userId, UpdateProfileRequest request) {
        try {
            UserResource userResource = keycloak.realm(realm).users().get(userId);
            UserRepresentation user = userResource.toRepresentation();
            if (user == null) {
                throw new ResourceNotFoundException("User", "id", userId);
            }
            assertCurrentTenantAccess(userId, user);

            if (request.getFirstName() != null) {
                user.setFirstName(request.getFirstName());
            }
            if (request.getLastName() != null) {
                user.setLastName(request.getLastName());
            }

            // Store photoUrl and language in user attributes
            Map<String, List<String>> attrs = user.getAttributes() != null
                    ? new HashMap<>(user.getAttributes()) : new HashMap<>();
            if (request.getPhotoUrl() != null) {
                attrs.put("photoUrl", List.of(request.getPhotoUrl()));
            }
            if (request.getLanguage() != null) {
                attrs.put("language", List.of(request.getLanguage()));
            }
            user.setAttributes(attrs);

            userResource.update(user);
            log.info("Updated profile for user: {}", userId);
            return getUser(userId);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating profile for user {}: {}", userId, e.getMessage(), e);
            throw new BusinessException("PROFILE_UPDATE_FAILED", "Failed to update profile: " + e.getMessage());
        }
    }

    public void changeOwnPassword(String userId, ChangeOwnPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("PASSWORD_MISMATCH", "New password and confirmation do not match");
        }

        // Verify the current password against the same frontend client used for login.
        UserRepresentation userRep;
        try {
            userRep = keycloak.realm(realm).users().get(userId).toRepresentation();
        } catch (Exception e) {
            throw new ResourceNotFoundException("User", "id", userId);
        }

        try (var testKeycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .grantType("password")
                .clientId(frontendClientId)
                .username(userRep.getUsername())
                .password(request.getCurrentPassword())
                .build()) {
            // If this throws, the current password is wrong
            testKeycloak.tokenManager().getAccessToken();
        } catch (Exception e) {
            throw new BusinessException("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getNewPassword());
        credential.setTemporary(false);
        keycloak.realm(realm).users().get(userId).resetPassword(credential);
        log.info("Changed password for user: {}", userId);
    }

    /**
     * Assign a list of permission codes to a user.
     * Permissions are stored in Keycloak user attributes and included in JWT
     * via the frontend client's "permissions-mapper" protocol mapper.
     */
    public UserDto assignPermissions(String userId, List<String> permissions) {
        UserRepresentation user = keycloak.realm(realm).users().get(userId).toRepresentation();
        if (user == null) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
        assertCurrentTenantAccess(userId, user);

        List<String> normalizedPermissions = PermissionUtils.normalizePermissions(permissions);
        storePermissionsAttribute(userId, normalizedPermissions, USER_PERMISSIONS_SOURCE);
        log.info("Assigned {} permissions to user {}", permissions != null ? permissions.size() : 0, userId);
        return getUser(userId);
    }

    // --- private helpers ---

    private void storePermissionsAttribute(String userId, List<String> permissions, String source) {
        try {
            UserResource userResource = keycloak.realm(realm).users().get(userId);
            UserRepresentation user = userResource.toRepresentation();

            Map<String, List<String>> attrs = user.getAttributes() != null
                    ? new HashMap<>(user.getAttributes()) : new HashMap<>();
            if (permissions == null || permissions.isEmpty()) {
                attrs.remove(PERMISSIONS_ATTRIBUTE);
            } else {
                attrs.put(PERMISSIONS_ATTRIBUTE, permissions);
            }
            if (StringUtils.hasText(source)) {
                attrs.put(PERMISSIONS_SOURCE_ATTRIBUTE, List.of(source));
            } else {
                attrs.remove(PERMISSIONS_SOURCE_ATTRIBUTE);
            }
            user.setAttributes(attrs);

            userResource.update(user);
            log.debug("Stored {} permissions for user {}", permissions != null ? permissions.size() : 0, userId);
        } catch (Exception e) {
            log.error("Error storing permissions for user {}: {}", userId, e.getMessage(), e);
            throw new BusinessException("PERMISSIONS_UPDATE_FAILED",
                    "Failed to update permissions: " + e.getMessage());
        }
    }

    private void assignRole(String userId, String roleName) {
        try {
            RoleRepresentation role = keycloak.realm(realm).roles().get(roleName).toRepresentation();
            keycloak.realm(realm).users().get(userId).roles().realmLevel()
                .add(Collections.singletonList(role));
            log.info("Assigned role '{}' to user '{}'", roleName, userId);
        } catch (Exception e) {
            log.error("Error assigning role '{}' to user '{}': {}", roleName, userId, e.getMessage(), e);
            throw new BusinessException("ROLE_ASSIGNMENT_FAILED",
                "Failed to assign role '" + roleName + "': " + e.getMessage());
        }
    }

    private void removeAllRealmRoles(String userId) {
        try {
            List<RoleRepresentation> currentRoles = keycloak.realm(realm).users().get(userId)
                .roles().realmLevel().listEffective();
            if (!currentRoles.isEmpty()) {
                keycloak.realm(realm).users().get(userId).roles().realmLevel().remove(currentRoles);
            }
        } catch (Exception e) {
            log.warn("Could not remove existing roles for user '{}': {}", userId, e.getMessage());
        }
    }

    private String findUserIdByUsername(String username) {
        try {
            return keycloak.realm(realm).users().searchByUsername(username, true).stream()
                    .findFirst()
                    .map(UserRepresentation::getId)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not resolve Keycloak user by username '{}': {}", username, e.getMessage());
            return null;
        }
    }

    private String findUserIdByEmail(String email) {
        try {
            return keycloak.realm(realm).users().searchByEmail(email, true).stream()
                    .findFirst()
                    .map(UserRepresentation::getId)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not resolve Keycloak user by email '{}': {}", email, e.getMessage());
            return null;
        }
    }

    private UserDto toDto(String userId, UserRepresentation user) {
        Map<String, List<String>> attrs = user.getAttributes();
        String tenantId = attrs != null && attrs.containsKey("tenant_id")
                ? attrs.get("tenant_id").get(0) : null;

        List<String> roles = Collections.emptyList();
        try {
            roles = keycloak.realm(realm).users().get(userId).roles().realmLevel()
                .listEffective().stream()
                .map(RoleRepresentation::getName)
                .filter(name -> !name.startsWith("default-roles") && !name.equals("offline_access") && !name.equals("uma_authorization"))
                .map(name -> RoleNameUtils.toDisplayRoleName(tenantId, name))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Could not fetch roles for user {}: {}", userId, e.getMessage());
        }
        String photoUrl = attrs != null && attrs.containsKey("photoUrl")
                ? attrs.get("photoUrl").get(0) : null;
        String language = attrs != null && attrs.containsKey("language")
                ? attrs.get("language").get(0) : null;
        String staffIdRaw = attrs != null && attrs.containsKey("staff_id")
            ? attrs.get("staff_id").get(0) : null;
        List<UUID> branchIds = attrs != null && attrs.containsKey(BRANCH_IDS_ATTRIBUTE)
            ? parseUuidList(attrs.get(BRANCH_IDS_ATTRIBUTE)) : List.of();
        List<String> permissions = attrs != null && attrs.containsKey(PERMISSIONS_ATTRIBUTE)
                ? attrs.get(PERMISSIONS_ATTRIBUTE) : List.of();
        String permissionsSource = attrs != null && attrs.containsKey(PERMISSIONS_SOURCE_ATTRIBUTE)
                ? attrs.get(PERMISSIONS_SOURCE_ATTRIBUTE).get(0) : null;

        return UserDto.builder()
            .id(user.getId() != null ? user.getId() : userId)
            .username(user.getUsername())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .staffId(parseOptionalUuid(staffIdRaw))
            .branchIds(branchIds)
            .roles(roles)
            .permissions(permissions)
            .permissionsSource(permissionsSource)
            .enabled(Boolean.TRUE.equals(user.isEnabled()))
            .photoUrl(photoUrl)
            .language(language)
            .build();
    }

    private java.util.UUID parseOptionalUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring malformed staff_id attribute: {}", raw);
            return null;
        }
    }

    private List<UUID> parseUuidList(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }

        List<UUID> result = new ArrayList<>(rawValues.size());
        for (String raw : rawValues) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            try {
                result.add(UUID.fromString(raw.trim()));
            } catch (IllegalArgumentException ex) {
                log.warn("Ignoring malformed UUID attribute value: {}", raw);
            }
        }
        return result;
    }

    private void applyRoleAndPermissions(String userId, String keycloakRoleName, List<String> explicitPermissions) {
        if (explicitPermissions != null) {
            storePermissionsAttribute(userId, PermissionUtils.normalizePermissions(explicitPermissions), USER_PERMISSIONS_SOURCE);
            return;
        }

        // Try role-stored permissions first (custom roles have these in Keycloak attributes)
        List<String> rolePermissions = keycloakRoleService.getRolePermissions(keycloakRoleName);
        if (!rolePermissions.isEmpty()) {
            storePermissionsAttribute(userId, rolePermissions, rolePermissionsSource(keycloakRoleName));
            return;
        }

        // Fall back to built-in default permissions for system roles (MANAGER, RECEPTIONIST, etc.)
        String displayRoleName = RoleNameUtils.toDisplayRoleName(null, keycloakRoleName);
        List<String> defaultPermissions = DefaultRolePermissions.forRole(displayRoleName);
        if (!defaultPermissions.isEmpty()) {
            log.info("Applying default permissions for system role '{}' to user {}", displayRoleName, userId);
            storePermissionsAttribute(userId, defaultPermissions, rolePermissionsSource(keycloakRoleName));
        } else {
            storePermissionsAttribute(userId, List.of(), rolePermissionsSource(keycloakRoleName));
        }
    }

    private Map<String, List<String>> buildUserAttributes(String tenantId, UUID staffId, List<String> branchIds) {
        Map<String, List<String>> attrs = new HashMap<>();
        mergeUserAttributes(attrs, tenantId, staffId, branchIds);
        return attrs;
    }

    private void mergeUserAttributes(Map<String, List<String>> attrs,
                                     String tenantId,
                                     UUID staffId,
                                     List<String> branchIds) {
        if (StringUtils.hasText(tenantId)) {
            attrs.put("tenant_id", List.of(tenantId));
        }

        if (staffId != null) {
            attrs.put("staff_id", List.of(staffId.toString()));
        }

        if (branchIds != null) {
            if (branchIds.isEmpty()) {
                attrs.remove(BRANCH_IDS_ATTRIBUTE);
            } else {
                attrs.put(BRANCH_IDS_ATTRIBUTE, branchIds);
            }
        }
    }

    private List<String> resolveAndValidateRequestedBranchIds(List<UUID> requestedBranchIds) {
        Set<String> requested = new HashSet<>();
        if (requestedBranchIds != null) {
            requested.addAll(requestedBranchIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(UUID::toString)
                    .collect(Collectors.toSet()));
        }

        String selectedBranchId = resolveCurrentSelectedBranchId();
        if (requested.isEmpty() && StringUtils.hasText(selectedBranchId)) {
            requested.add(selectedBranchId);
        }

        Set<String> creatorAllowedBranchIds = currentAllowedBranchIds();
        if (!creatorAllowedBranchIds.isEmpty()) {
            if (requested.isEmpty()) {
                if (creatorAllowedBranchIds.size() == 1) {
                    requested.addAll(creatorAllowedBranchIds);
                } else {
                    throw new BusinessException(
                            "BRANCH_SCOPE_REQUIRED",
                            "Specify branchIds when creator has branch-scoped access"
                    );
                }
            }

            if (!creatorAllowedBranchIds.containsAll(requested)) {
                throw new BusinessException(
                        "BRANCH_ACCESS_DENIED",
                        "Cannot assign user access to branches outside creator scope"
                );
            }
        }

        return requested.stream().sorted().toList();
    }

    private String resolveCurrentSelectedBranchId() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }

        HttpServletRequest request = servletRequestAttributes.getRequest();
        String fromHeader = normalizeBranchId(request.getHeader("X-Branch-ID"));
        if (StringUtils.hasText(fromHeader)) {
            return fromHeader;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return normalizeBranchId(jwt.getClaimAsString("branch_id"));
        }

        return null;
    }

    private Set<String> currentAllowedBranchIds() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Set.of();
        }

        List<String> claim = jwt.getClaimAsStringList(BRANCH_IDS_ATTRIBUTE);
        if (claim == null || claim.isEmpty()) {
            String csv = jwt.getClaimAsString(BRANCH_IDS_ATTRIBUTE);
            if (StringUtils.hasText(csv)) {
                claim = Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .toList();
            }
        }

        if (claim == null || claim.isEmpty()) {
            return Set.of();
        }

        Set<String> result = new HashSet<>();
        for (String raw : claim) {
            String normalized = normalizeBranchId(raw);
            if (StringUtils.hasText(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String normalizeBranchId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }

        try {
            return UUID.fromString(raw.trim()).toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String resolveTenantId(String requestedTenantId) {
        String currentTenantId = currentTenantId();
        if (StringUtils.hasText(currentTenantId)) {
            return currentTenantId;
        }

        if (StringUtils.hasText(requestedTenantId)) {
            return requestedTenantId;
        }

        return null;
    }

    private String currentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String tenantId = jwt.getClaimAsString("tenant_id");
            if (StringUtils.hasText(tenantId)) {
                return tenantId;
            }
        }

        return null;
    }

    private String resolveTenantIdForExistingUser(UserRepresentation user) {
        if (user.getAttributes() != null) {
            List<String> tenantIds = user.getAttributes().get("tenant_id");
            if (tenantIds != null && !tenantIds.isEmpty() && StringUtils.hasText(tenantIds.get(0))) {
                return tenantIds.get(0);
            }
        }
        return resolveTenantId(null);
    }

    private String resolveKeycloakRoleName(String tenantId, String requestedRoleName) {
        try {
            return RoleNameUtils.toKeycloakRoleName(tenantId, requestedRoleName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("ROLE_RESOLUTION_FAILED", e.getMessage());
        }
    }

    static String rolePermissionsSource(String keycloakRoleName) {
        return RoleNameUtils.rolePermissionsSource(keycloakRoleName);
    }

    private void assertCurrentTenantAccess(String userId, UserRepresentation user) {
        String tenantId = currentTenantId();
        if (!StringUtils.hasText(tenantId)) {
            return;
        }
        if (!belongsToTenant(user, tenantId)) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
    }

    private boolean belongsToTenant(UserRepresentation user, String tenantId) {
        if (!StringUtils.hasText(tenantId) || user.getAttributes() == null) {
            return false;
        }
        List<String> tenantIds = user.getAttributes().get("tenant_id");
        return tenantIds != null && tenantIds.stream().anyMatch(tenantId::equals);
    }
}
