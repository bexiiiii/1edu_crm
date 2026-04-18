package com.ondeedu.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiPayBillingScheduler {

    private final ApiPayInvoiceService apiPayInvoiceService;

    @Value("${payment.apipay.billing.enabled:true}")
    private boolean billingEnabled;

    @Scheduled(cron = "${payment.apipay.billing.cron:0 15 20 28-31 * *}")
    public void generateEndOfMonthInvoices() {
        if (!billingEnabled) {
            return;
        }

        LocalDate today = LocalDate.now();
        boolean isLastDayOfMonth = !today.getMonth().equals(today.plusDays(1).getMonth());
        if (!isLastDayOfMonth) {
            return;
        }

        YearMonth targetMonth = YearMonth.from(today.plusMonths(1));
        try {
            var result = apiPayInvoiceService.generateMonthlyInvoices(targetMonth.toString());
            log.info("ApiPay billing scheduler generated invoices for month {}: generated={}, skipped={}, failed={}",
                    result.getMonth(), result.getGenerated(), result.getSkipped(), result.getFailed());
        } catch (Exception e) {
            log.error("ApiPay billing scheduler failed for month {}", targetMonth, e);
        }
    }
}
