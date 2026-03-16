package com.ondeedu.lead.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLeadRequest {
    @NotBlank
    private String firstName;
    @NotBlank
    private String lastName;
    private String phone;
    private String email;
    private String source;
    private String courseInterest;
    private String notes;
    private String assignedTo;
}
