package com.ondeedu.auth.controller;

import com.ondeedu.auth.dto.CreateUserRequest;
import com.ondeedu.auth.dto.SyncRoleRequest;
import com.ondeedu.auth.service.KeycloakRoleService;
import com.ondeedu.auth.service.KeycloakUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final KeycloakUserService keycloakUserService;
    private final KeycloakRoleService keycloakRoleService;

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public void createUser(@Valid @RequestBody CreateUserRequest request) {
        keycloakUserService.createUser(request);
    }

    @PutMapping("/roles/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void syncRole(@PathVariable String name, @Valid @RequestBody SyncRoleRequest request) {
        keycloakRoleService.syncRole(request.getTenantId(), name, request.getDescription(), request.getPermissions());
    }

    @DeleteMapping("/roles/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable String name, @RequestParam String tenantId) {
        keycloakRoleService.deleteRole(tenantId, name);
    }
}
