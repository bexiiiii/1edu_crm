package com.ondeedu.auth.controller;

import com.ondeedu.auth.dto.ChangeOwnPasswordRequest;
import com.ondeedu.auth.dto.UpdateProfileRequest;
import com.ondeedu.auth.dto.UserDto;
import com.ondeedu.auth.service.KeycloakUserService;
import com.ondeedu.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Current user profile management")
public class ProfileController {

    private final KeycloakUserService keycloakUserService;

    @GetMapping
    @Operation(summary = "Get current user profile")
    public ApiResponse<UserDto> getProfile(@AuthenticationPrincipal Jwt jwt) {
        String userId = keycloakUserService.resolveCurrentUserId(jwt);
        return ApiResponse.success(keycloakUserService.getProfile(userId));
    }

    @PutMapping
    @Operation(summary = "Update current user profile (name, photo, language)")
    public ApiResponse<UserDto> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateProfileRequest request) {
        String userId = keycloakUserService.resolveCurrentUserId(jwt);
        return ApiResponse.success(keycloakUserService.updateProfile(userId, request), "Profile updated successfully");
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change current user password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangeOwnPasswordRequest request) {
        String userId = keycloakUserService.resolveCurrentUserId(jwt);
        keycloakUserService.changeOwnPassword(userId, request);
        return ApiResponse.success("Password changed successfully");
    }
}
