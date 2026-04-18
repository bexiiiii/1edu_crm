package com.ondeedu.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateKpayInvoicesResponse {

    private String month;
    private int totalSubscriptions;
    private int generated;
    private int skipped;
    private int failed;
}
