package com.ondeedu.lead.dto;

import com.ondeedu.lead.entity.LeadStage;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phone;
    private String email;
    private LeadStage stage;
    private String source;
    private String courseInterest;
    private String notes;
    private String assignedTo;
    private Instant createdAt;
    private Instant updatedAt;
}
