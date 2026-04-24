package com.ondeedu.inventory.service;

import com.ondeedu.inventory.dto.InventoryItemDto;
import com.ondeedu.inventory.dto.InventoryReportDto;
import com.ondeedu.inventory.dto.InventoryTransactionDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class InventoryExcelExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public byte[] exportItems(List<InventoryItemDto> items) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle titleStyle = createTitleStyle(wb);

            Sheet sheet = wb.createSheet("Список товаров");

            int r = 0;
            Row titleRow = sheet.createRow(r++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Список товаров УЦ — " + LocalDate.now().format(DATE_FMT));
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));
            r++;

            writeHeader(sheet, r++, headerStyle,
                "№", "Наименование", "Категория", "Ед.изм.", "Кол-во",
                "Мин.кол-во", "Цена за ед. (KZT)", "Стоимость (KZT)",
                "Статус", "Местонахождение", "Поставщик", "Артикул");

            int n = 1;
            BigDecimal grandTotal = BigDecimal.ZERO;
            for (InventoryItemDto item : items) {
                Row row = sheet.createRow(r++);
                setCell(row, 0, n++);
                setCell(row, 1, item.getName());
                setCell(row, 2, item.getCategoryName() != null ? item.getCategoryName() : "");
                setCell(row, 3, item.getUnitAbbreviation() != null ? item.getUnitAbbreviation() : "");
                setCell(row, 4, item.getQuantity());
                setCell(row, 5, item.getMinQuantity());
                setCell(row, 6, item.getPricePerUnit());
                BigDecimal tv = item.getTotalValue() != null ? item.getTotalValue() : BigDecimal.ZERO;
                setCell(row, 7, tv);
                setCell(row, 8, translateStatus(item.getStatus()));
                setCell(row, 9, item.getLocation() != null ? item.getLocation() : "");
                setCell(row, 10, item.getSupplier() != null ? item.getSupplier() : "");
                setCell(row, 11, item.getSku() != null ? item.getSku() : "");
                grandTotal = grandTotal.add(tv);
            }

            r++;
            Row totalRow = sheet.createRow(r);
            Cell totalLabel = totalRow.createCell(6);
            totalLabel.setCellValue("Итого:");
            totalLabel.setCellStyle(headerStyle);
            Cell totalValue = totalRow.createCell(7);
            totalValue.setCellValue(grandTotal.doubleValue());
            totalValue.setCellStyle(headerStyle);

            for (int i = 0; i <= 11; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate items Excel", e);
        }
    }

    public byte[] exportTransactions(List<InventoryTransactionDto> transactions, LocalDate from, LocalDate to) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle titleStyle = createTitleStyle(wb);

            Sheet sheet = wb.createSheet("Движение товаров");

            int r = 0;
            Row titleRow = sheet.createRow(r++);
            Cell titleCell = titleRow.createCell(0);
            String period = (from != null ? from.format(DATE_FMT) : "начало") + " — " + (to != null ? to.format(DATE_FMT) : "сейчас");
            titleCell.setCellValue("Журнал движения товаров УЦ — " + period);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));
            r++;

            writeHeader(sheet, r++, headerStyle,
                "Дата/Время", "Товар", "Тип операции", "Кол-во",
                "До операции", "После операции", "Цена (KZT)", "Сумма (KZT)",
                "Причина/Примечание", "Документ");

            for (InventoryTransactionDto t : transactions) {
                Row row = sheet.createRow(r++);
                setCell(row, 0, t.getTransactionDate() != null ? t.getTransactionDate().format(DATETIME_FMT) : "");
                setCell(row, 1, t.getItemName() != null ? t.getItemName() : "");
                setCell(row, 2, translateTransactionType(t.getTransactionType()));
                setCell(row, 3, t.getQuantity());
                setCell(row, 4, t.getQuantityBefore());
                setCell(row, 5, t.getQuantityAfter());
                setCell(row, 6, t.getUnitCost());
                setCell(row, 7, t.getTotalCost());
                String noteReason = joinNonEmpty(t.getNotes(), t.getReason());
                setCell(row, 8, noteReason);
                setCell(row, 9, t.getReferenceNumber() != null ? t.getReferenceNumber() : "");
            }

            for (int i = 0; i <= 9; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate transactions Excel", e);
        }
    }

    public byte[] exportInventoryReport(InventoryReportDto report) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle titleStyle = createTitleStyle(wb);

            // Sheet 1 — Summary
            Sheet summary = wb.createSheet("Сводка");
            int r = 0;

            Row titleRow = summary.createRow(r++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Инвентаризация УЦ — " + report.getReportDate().format(DATE_FMT));
            titleCell.setCellStyle(titleStyle);
            summary.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
            r++;

            r = writeKv(summary, r, "Дата инвентаризации", report.getReportDate().format(DATE_FMT));
            r = writeKv(summary, r, "Всего позиций", report.getTotalItems());
            r = writeKv(summary, r, "В наличии", report.getInStockCount());
            r = writeKv(summary, r, "Мало на складе", report.getLowStockCount());
            r = writeKv(summary, r, "Нет в наличии", report.getOutOfStockCount());
            r++;
            Row totalRow = summary.createRow(r++);
            Cell totalLabel = totalRow.createCell(0);
            totalLabel.setCellValue("Общая стоимость склада (KZT)");
            totalLabel.setCellStyle(headerStyle);
            Cell totalVal = totalRow.createCell(1);
            BigDecimal tv = report.getTotalInventoryValue() != null ? report.getTotalInventoryValue() : BigDecimal.ZERO;
            totalVal.setCellValue(tv.doubleValue());
            totalVal.setCellStyle(headerStyle);
            summary.autoSizeColumn(0);
            summary.autoSizeColumn(1);

            // Sheet 2 — Full list
            Sheet sheet = wb.createSheet("Опись товаров");
            r = 0;

            Row itemsTitle = sheet.createRow(r++);
            Cell itCell = itemsTitle.createCell(0);
            itCell.setCellValue("Опись товарно-материальных ценностей на " + report.getReportDate().format(DATE_FMT));
            itCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));
            r++;

            writeHeader(sheet, r++, headerStyle,
                "№", "Наименование", "Категория", "Ед.изм.",
                "Факт.кол-во", "Мин.кол-во", "Цена за ед. (KZT)",
                "Стоимость (KZT)", "Статус", "Местонахождение");

            int n = 1;
            for (InventoryItemDto item : report.getItems()) {
                Row row = sheet.createRow(r++);
                setCell(row, 0, n++);
                setCell(row, 1, item.getName());
                setCell(row, 2, item.getCategoryName() != null ? item.getCategoryName() : "");
                setCell(row, 3, item.getUnitAbbreviation() != null ? item.getUnitAbbreviation() : "");
                setCell(row, 4, item.getQuantity());
                setCell(row, 5, item.getMinQuantity());
                setCell(row, 6, item.getPricePerUnit());
                setCell(row, 7, item.getTotalValue() != null ? item.getTotalValue() : BigDecimal.ZERO);
                setCell(row, 8, translateStatus(item.getStatus()));
                setCell(row, 9, item.getLocation() != null ? item.getLocation() : "");
            }

            r++;
            Row footerRow = sheet.createRow(r);
            Cell sigLabel = footerRow.createCell(0);
            sigLabel.setCellValue("Инвентаризацию провёл: ___________________  Дата: " + report.getReportDate().format(DATE_FMT));

            for (int i = 0; i <= 9; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate inventory report Excel", e);
        }
    }

    // ==================== Helpers ====================

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private void writeHeader(Sheet sheet, int rowNum, CellStyle style, String... headers) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private int writeKv(Sheet sheet, int rowNum, String key, Object value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(key);
        Cell vc = row.createCell(1);
        if (value instanceof Number n) vc.setCellValue(n.doubleValue());
        else vc.setCellValue(value != null ? value.toString() : "");
        return rowNum + 1;
    }

    private void setCell(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof BigDecimal bd) {
            cell.setCellValue(bd.doubleValue());
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private String translateStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "IN_STOCK" -> "В наличии";
            case "LOW_STOCK" -> "Мало";
            case "OUT_OF_STOCK" -> "Нет в наличии";
            case "DISCONTINUED" -> "Снят с учёта";
            default -> status;
        };
    }

    private String translateTransactionType(String type) {
        if (type == null) return "";
        return switch (type) {
            case "RECEIVED" -> "Приход";
            case "ISSUED" -> "Расход";
            case "RETURNED" -> "Возврат";
            case "ADJUSTMENT" -> "Корректировка";
            case "WRITE_OFF" -> "Списание";
            case "TRANSFER" -> "Перемещение";
            default -> type;
        };
    }

    private String joinNonEmpty(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (!sb.isEmpty()) sb.append("; ");
                sb.append(p);
            }
        }
        return sb.toString();
    }
}
