package com.ondeedu.report.controller;

import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.report.model.ReportFormat;
import com.ondeedu.report.model.ReportType;
import com.ondeedu.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/generate")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER') or hasAuthority('REPORTS_VIEW')")
    public ResponseEntity<byte[]> generateReport(
            @RequestParam ReportType type,
            @RequestParam ReportFormat format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        String tenantId = TenantContext.getTenantId();
        byte[] content = reportService.generateReport(type, format, tenantId, from, to);

        HttpHeaders headers = new HttpHeaders();
        if (format == ReportFormat.PDF) {
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("report.pdf")
                    .build());
        } else {
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("report.xlsx")
                    .build());
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }
}
