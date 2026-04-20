package com.ondeedu.auth.dto;

import jakarta.validation.constraints.Email;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UpdateUserRequest {

    @Email(message = "Email must be valid")
    private String email;

    private String firstName;

    private String lastName;

    private String role;

    /** Optional: link auth account to staff profile in staff-service */
    private UUID staffId;

    /** Optional: replace branch-scoped access list for this user */
    private List<UUID> branchIds;

    /** Optional: replace all permissions for this user */
    private List<String> permissions;
}
