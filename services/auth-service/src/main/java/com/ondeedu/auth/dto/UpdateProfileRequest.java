package com.ondeedu.auth.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String firstName;
    private String lastName;
    private String photoUrl;
    private String language;
}
