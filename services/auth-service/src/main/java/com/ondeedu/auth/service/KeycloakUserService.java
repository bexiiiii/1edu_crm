package com.ondeedu.auth.service;

import com.ondeedu.auth.dto.ChangeOwnPasswordRequest;
import com.ondeedu.auth.dto.ChangePasswordRequest;
import com.ondeedu.auth.dto.CreateUserRequest;
import com.ondeedu.auth.dto.UpdateProfileRequest;
import com.ondeedu.auth.dto.UpdateUserRequest;
import com.ondeedu.auth.dto.UserDto;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KeycloakUserService {

    private final Keycloak keycloak;
    private final String realm;

    @Value("${keycloak.server-url}")
    private String serverUrl;

    public KeycloakUserService(Keycloak keycloak,
                               @Value("${keycloak.realm}") String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    public UserDto createUser(CreateUserRequest request) {
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

        // Set tenant_id attribute so Keycloak includes it in JWT via protocol mapper
        if (request.getTenantId() != null && !request.getTenantId().isBlank()) {
            Map<String, List<String>> attrs = new HashMap<>();
            attrs.put("tenant_id", List.of(request.getTenantId()));
            user.setAttributes(attrs);
        }

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

            // Set tenant_id attribute via separate update (more reliable than setting in create payload)
            if (request.getTenantId() != null && !request.getTenantId().isBlank()) {
                UserResource userResource = keycloak.realm(realm).users().get(userId);
                UserRepresentation createdUser = userResource.toRepresentation();
                Map<String, List<String>> attrs = createdUser.getAttributes() != null
                        ? new HashMap<>(createdUser.getAttributes()) : new HashMap<>();
                attrs.put("tenant_id", List.of(request.getTenantId()));
                createdUser.setAttributes(attrs);
                userResource.update(createdUser);
                log.info("Set tenant_id={} for user: {}", request.getTenantId(), userId);
            }

            // Assign realm role
            assignRole(userId, request.getRole());

            // Store custom permissions in user attributes (sent to JWT via protocol mapper)
            if (request.getPermissions() != null && !request.getPermissions().isEmpty()) {
                storePermissionsAttribute(userId, request.getPermissions());
            }

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

            if (request.getEmail() != null) {
                user.setEmail(request.getEmail());
            }
            if (request.getFirstName() != null) {
                user.setFirstName(request.getFirstName());
            }
            if (request.getLastName() != null) {
                user.setLastName(request.getLastName());
            }

            userResource.update(user);
            log.info("Updated Keycloak user: {}", userId);

            if (request.getRole() != null) {
                // Remove all existing realm roles and assign the new one
                removeAllRealmRoles(userId);
                assignRole(userId, request.getRole());
            }

            if (request.getPermissions() != null) {
                storePermissionsAttribute(userId, request.getPermissions());
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
            List<UserRepresentation> users = keycloak.realm(realm).users()
                .search(search, page * size, size);
            return users.stream()
                .map(u -> toDto(u.getId(), u))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error listing users: {}", e.getMessage(), e);
            throw new BusinessException("USER_LIST_FAILED", "Failed to list users: " + e.getMessage());
        }
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

        // Verify current password via direct grant
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
                .clientId("ondeedu-app")
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
     * via the "permissions-mapper" protocol mapper set up by KeycloakSetupService.
     */
    public UserDto assignPermissions(String userId, List<String> permissions) {
        storePermissionsAttribute(userId, permissions);
        log.info("Assigned {} permissions to user {}", permissions.size(), userId);
        return getUser(userId);
    }

    // --- private helpers ---

    private void storePermissionsAttribute(String userId, List<String> permissions) {
        try {
            UserResource userResource = keycloak.realm(realm).users().get(userId);
            UserRepresentation user = userResource.toRepresentation();

            Map<String, List<String>> attrs = user.getAttributes() != null
                    ? new HashMap<>(user.getAttributes()) : new HashMap<>();
            attrs.put("permissions", permissions);
            user.setAttributes(attrs);

            userResource.update(user);
            log.debug("Stored {} permissions for user {}", permissions.size(), userId);
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

    private UserDto toDto(String userId, UserRepresentation user) {
        List<String> roles = Collections.emptyList();
        try {
            roles = keycloak.realm(realm).users().get(userId).roles().realmLevel()
                .listEffective().stream()
                .map(RoleRepresentation::getName)
                .filter(name -> !name.startsWith("default-roles") && !name.equals("offline_access") && !name.equals("uma_authorization"))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Could not fetch roles for user {}: {}", userId, e.getMessage());
        }

        Map<String, List<String>> attrs = user.getAttributes();
        String photoUrl = attrs != null && attrs.containsKey("photoUrl")
                ? attrs.get("photoUrl").get(0) : null;
        String language = attrs != null && attrs.containsKey("language")
                ? attrs.get("language").get(0) : null;

        return UserDto.builder()
            .id(user.getId() != null ? user.getId() : userId)
            .username(user.getUsername())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .roles(roles)
            .enabled(Boolean.TRUE.equals(user.isEnabled()))
            .photoUrl(photoUrl)
            .language(language)
            .build();
    }
}
