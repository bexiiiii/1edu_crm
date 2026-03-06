package com.ondeedu.auth.dto;

import jakarta.validation.constraints.Email;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UpdateUserRequest {

    @Email(message = "Email must be valid")
    private String email;

    private String firstName;

    private String lastName;

    private String role;

    /** Optional: replace all permissions for this user */
    private List<String> permissions;
}
