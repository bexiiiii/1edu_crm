package com.ondeedu.lead.dto;

import com.ondeedu.lead.entity.LeadStage;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLeadRequest {
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private LeadStage stage;
    private String source;
    private String courseInterest;
    private String notes;
    private String assignedTo;
}
