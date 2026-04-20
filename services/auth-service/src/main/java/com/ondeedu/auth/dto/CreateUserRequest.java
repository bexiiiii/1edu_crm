package com.ondeedu.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @Email(message = "Email must be valid")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Role is required")
    private String role;

    /** Optional: link auth account to staff profile in staff-service */
    private UUID staffId;

    /** Optional: restrict account access to selected tenant branches */
    private List<UUID> branchIds;

    private String tenantId;

    /** Optional: permission codes from RoleConfig (e.g. ["STUDENTS_VIEW","LEADS_CREATE"]) */
    private List<String> permissions;
}
