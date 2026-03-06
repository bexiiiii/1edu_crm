package com.ondeedu.report.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.report.client.AnalyticsGrpcClient;
import com.ondeedu.report.model.ReportFormat;
import com.ondeedu.report.model.ReportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AnalyticsGrpcClient analyticsGrpcClient;

    public byte[] generateReport(ReportType type, ReportFormat format,
                                  String tenantId, LocalDate from, LocalDate to) {
        String data = fetchData(type, from, to);

        if (format == ReportFormat.PDF) {
            return generatePdf(data, type);
        } else {
            return generateExcel(data, type);
        }
    }

    private String fetchData(ReportType type, LocalDate from, LocalDate to) {
        try {
            return switch (type) {
                case DASHBOARD -> analyticsGrpcClient.getDashboardJson(from, to);
                case FINANCE -> analyticsGrpcClient.getFinanceReportJson(from, to);
                default -> {
                    log.warn("No specific analytics endpoint for ReportType {}, using dashboard", type);
                    yield analyticsGrpcClient.getDashboardJson(from, to);
                }
            };
        } catch (Exception e) {
            log.error("Failed to fetch analytics data for report type {}: {}", type, e.getMessage());
            throw new BusinessException("ANALYTICS_FETCH_FAILED",
                    "Failed to fetch analytics data: " + e.getMessage());
        }
    }

    private byte[] generatePdf(String data, ReportType type) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            document.add(new Paragraph(type.name() + " REPORT")
                    .setBold()
                    .setFontSize(18));

            document.add(new Paragraph("Generated at: " + LocalDateTime.now())
                    .setFontSize(10));

            document.add(new Paragraph(" "));

            document.add(new Paragraph(data != null ? data : "No data available").setFontSize(10));

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF report: {}", e.getMessage());
            throw new BusinessException("PDF_GENERATION_FAILED",
                    "Failed to generate PDF report: " + e.getMessage());
        }
    }

    private byte[] generateExcel(String data, ReportType type) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet(type.name());

            XSSFRow headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Report Type");
            headerRow.createCell(1).setCellValue("Generated At");
            headerRow.createCell(2).setCellValue("Data");

            XSSFRow dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue(type.name());
            dataRow.createCell(1).setCellValue(LocalDateTime.now().toString());
            dataRow.createCell(2).setCellValue(data != null ? data : "No data available");

            for (int i = 0; i < 3; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Excel report: {}", e.getMessage());
            throw new BusinessException("EXCEL_GENERATION_FAILED",
                    "Failed to generate Excel report: " + e.getMessage());
        }
    }
}
