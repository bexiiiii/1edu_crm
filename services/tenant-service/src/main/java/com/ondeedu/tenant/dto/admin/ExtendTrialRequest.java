package com.ondeedu.tenant.dto.admin;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ExtendTrialRequest {

    @NotNull
    @Future
    private LocalDate trialEndsAt;

    private String reason;
}
