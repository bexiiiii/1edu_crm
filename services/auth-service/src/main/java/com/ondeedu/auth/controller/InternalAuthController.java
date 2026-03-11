package com.ondeedu.auth.controller;

import com.ondeedu.auth.dto.CreateUserRequest;
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

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public void createUser(@Valid @RequestBody CreateUserRequest request) {
        keycloakUserService.createUser(request);
    }
}
