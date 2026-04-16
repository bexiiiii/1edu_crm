package com.ondeedu.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserDto {

    private String id;

    private String username;

    private String email;

    private String firstName;

    private String lastName;

    private UUID staffId;

    private List<String> roles;

    /** Current permission codes assigned to this user (from Keycloak attribute) */
    private List<String> permissions;

    /**
     * Source of permissions: "USER" = custom override, "ROLE:<roleName>" = inherited from role.
     * Null means no permissions were explicitly assigned.
     */
    private String permissionsSource;

    private boolean enabled;

    private String photoUrl;

    private String language;
}
