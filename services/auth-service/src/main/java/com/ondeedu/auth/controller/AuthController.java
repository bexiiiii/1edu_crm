package com.ondeedu.auth.controller;

import com.ondeedu.auth.dto.ChangePasswordRequest;
import com.ondeedu.auth.dto.CreateUserRequest;
import com.ondeedu.auth.dto.UpdateUserRequest;
import com.ondeedu.auth.dto.UserDto;
import com.ondeedu.auth.service.KeycloakUserService;
import com.ondeedu.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
@Tag(name = "Auth / Users", description = "Keycloak user management API")
public class AuthController {

    private final KeycloakUserService keycloakUserService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Create a new Keycloak user")
    public ApiResponse<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDto user = keycloakUserService.createUser(request);
        return ApiResponse.success(user, "User created successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Get Keycloak user by ID")
    public ApiResponse<UserDto> getUser(@PathVariable String id) {
        return ApiResponse.success(keycloakUserService.getUser(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Update Keycloak user")
    public ApiResponse<UserDto> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserDto user = keycloakUserService.updateUser(id, request);
        return ApiResponse.success(user, "User updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Disable Keycloak user (soft delete)")
    public ApiResponse<Void> deleteUser(@PathVariable String id) {
        keycloakUserService.deleteUser(id);
        return ApiResponse.success("User disabled successfully");
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Reset password for a Keycloak user")
    public ApiResponse<Void> resetPassword(
            @PathVariable String id,
            @Valid @RequestBody ChangePasswordRequest request) {
        keycloakUserService.resetPassword(id, request);
        return ApiResponse.success("Password reset successfully");
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Assign permission codes to a user (from RoleConfig)")
    public ApiResponse<UserDto> assignPermissions(
            @PathVariable String id,
            @RequestBody List<String> permissions) {
        UserDto user = keycloakUserService.assignPermissions(id, permissions);
        return ApiResponse.success(user, "Permissions assigned successfully");
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "List Keycloak users with optional search")
    public ApiResponse<List<UserDto>> listUsers(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        List<UserDto> users = keycloakUserService.listUsers(search, page, size);
        return ApiResponse.success(users);
    }
}
