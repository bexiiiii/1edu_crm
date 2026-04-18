package com.ondeedu.analytics.export;

import com.ondeedu.analytics.dto.response.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnalyticsExcelExportService {

    public byte[] exportDashboard(DashboardResponse data, LocalDate from, LocalDate to, String lessonType) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet summary = workbook.createSheet("Сводка");
            int r = 0;
            r = writeSectionHeader(summary, r, "Параметры отчета", headerStyle);
            r = writeKv(summary, r, "Период от", from);
            r = writeKv(summary, r, "Период до", to);
            r = writeKv(summary, r, "Тип занятий", lessonType);
            r++;

            r = writeSectionHeader(summary, r, "Ключевые метрики", headerStyle);
            r = writeKv(summary, r, "Посещаемость (%)", data.getAttendanceRate());
            r = writeKv(summary, r, "Посещаемость пред. период (%)", data.getAttendancePrevRate());
            r = writeKv(summary, r, "Загрузка групп (%)", data.getGroupLoadRate());
            r = writeKv(summary, r, "Пробных назначено", data.getTrialScheduled());
            r = writeKv(summary, r, "Пробных посещено", data.getTrialAttended());
            r = writeKv(summary, r, "Конверсия пробных (%)", data.getTrialConversionRate());
            r = writeKv(summary, r, "Средний чек", data.getAverageCheck());
            r = writeKv(summary, r, "ARPU", data.getArpu());
            r = writeKv(summary, r, "Продано абонементов", data.getSubscriptionsSold());
            r = writeKv(summary, r, "Выручка", data.getRevenue());
            r = writeKv(summary, r, "Расходы", data.getExpenses());
            r = writeKv(summary, r, "Прибыль", data.getProfit());
            r = writeKv(summary, r, "Лиды", data.getLeadsTotal());
            r = writeKv(summary, r, "Договоры", data.getContractsTotal());
            r = writeKv(summary, r, "Конверсия лид->договор (%)", data.getLeadsToContractsConversion());
            r = writeKv(summary, r, "Удержание M+1 (%)", data.getRetentionM1Rate());

            if (data.getTopEmployee() != null) {
                r++;
                r = writeSectionHeader(summary, r, "Лучший сотрудник", headerStyle);
                TopEmployeeDto te = data.getTopEmployee();
                r = writeKv(summary, r, "Сотрудник", te.getFullName());
                r = writeKv(summary, r, "Индекс", te.getIndex());
                r = writeKv(summary, r, "Выручка", te.getRevenue());
                r = writeKv(summary, r, "Загрузка групп (%)", te.getGroupLoadRate());
                r = writeKv(summary, r, "Активные ученики", te.getActiveStudents());
            }

            autoSize(summary, 2);

            Sheet monthly = workbook.createSheet("Посещаемость_по_месяцам");
            writeHeader(monthly, 0, headerStyle, "Месяц", "Посещаемость (%)");
            int mr = 1;
            for (MonthlyAttendanceDto row : safeList(data.getMonthlyAttendance())) {
                Row x = monthly.createRow(mr++);
                setCell(x, 0, row.getMonth());
                setCell(x, 1, row.getRate());
            }
            autoSize(monthly, 2);

            Sheet joined = workbook.createSheet("Пришли_ученики");
            writeHeader(joined, 0, headerStyle, "ID", "ФИО");
            int jr = 1;
            for (StudentMovementDto row : safeList(data.getJoinedStudents())) {
                Row x = joined.createRow(jr++);
                setCell(x, 0, row.getStudentId());
                setCell(x, 1, row.getFullName());
            }
            autoSize(joined, 2);

            Sheet left = workbook.createSheet("Ушли_ученики");
            writeHeader(left, 0, headerStyle, "ID", "ФИО");
            int lr = 1;
            for (StudentMovementDto row : safeList(data.getLeftStudents())) {
                Row x = left.createRow(lr++);
                setCell(x, 0, row.getStudentId());
                setCell(x, 1, row.getFullName());
            }
            autoSize(left, 2);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate dashboard Excel report", e);
        }
    }

    public byte[] exportFinanceReport(FinanceReportResponse data, LocalDate from, LocalDate to) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet summary = workbook.createSheet("Сводка");
            int r = 0;
            r = writeSectionHeader(summary, r, "Параметры отчета", headerStyle);
            r = writeKv(summary, r, "Период от", from);
            r = writeKv(summary, r, "Период до", to);
            r++;
            r = writeSectionHeader(summary, r, "Итоги", headerStyle);
            r = writeKv(summary, r, "Выручка", data.getRevenue());
            r = writeKv(summary, r, "Выручка Δ (%)", data.getRevenueDeltaPct());
            r = writeKv(summary, r, "Расходы", data.getExpenses());
            r = writeKv(summary, r, "Расходы Δ (%)", data.getExpensesDeltaPct());
            r = writeKv(summary, r, "Прибыль", data.getProfit());
            r = writeKv(summary, r, "Прибыль Δ (%)", data.getProfitDeltaPct());

            if (data.getReconciliation() != null) {
                SubscriptionReconciliationDto rec = data.getReconciliation();
                r++;
                r = writeSectionHeader(summary, r, "Сверка абонементов", headerStyle);
                r = writeKv(summary, r, "Сумма абонементов", rec.getTotalSubscriptionAmount());
                r = writeKv(summary, r, "Выручка от абонементов", rec.getRevenueFromSubscriptions());
                r = writeKv(summary, r, "Оплачено до периода", rec.getPaidBeforePeriod());
                r = writeKv(summary, r, "Долг по абонементам", rec.getDebtFromSubscriptions());
                r = writeKv(summary, r, "Покрытие (%)", rec.getCoverageRate());
                r = writeKv(summary, r, "Студентов без оплат", rec.getStudentsWithoutPayments());
                r = writeKv(summary, r, "Абонементов без оплат", rec.getSubscriptionsWithoutPayments());
            }
            autoSize(summary, 2);

            Sheet monthly = workbook.createSheet("Динамика_по_месяцам");
            writeHeader(monthly, 0, headerStyle, "Месяц", "Label", "Выручка", "Расходы", "Прибыль");
            int mr = 1;
            for (MonthlyFinanceDto row : safeList(data.getMonthly())) {
                Row x = monthly.createRow(mr++);
                setCell(x, 0, row.getMonth());
                setCell(x, 1, row.getLabel());
                setCell(x, 2, row.getRevenue());
                setCell(x, 3, row.getExpenses());
                setCell(x, 4, row.getProfit());
            }
            autoSize(monthly, 5);

            writeCategorySheet(workbook, headerStyle, "Доходы_по_статьям", safeList(data.getRevenueByCategory()));
            writeCategorySheet(workbook, headerStyle, "Доходы_по_источникам", safeList(data.getRevenueBySource()));
            writeCategorySheet(workbook, headerStyle, "Расходы_по_статьям", safeList(data.getExpensesByCategory()));

            Sheet byGroup = workbook.createSheet("Доходы_по_группам");
            writeHeader(byGroup, 0, headerStyle, "Group ID", "Группа", "Выручка");
            int gr = 1;
            for (GroupRevenueDto row : safeList(data.getRevenueByGroup())) {
                Row x = byGroup.createRow(gr++);
                setCell(x, 0, row.getGroupId());
                setCell(x, 1, row.getGroupName());
                setCell(x, 2, row.getRevenue());
            }
            autoSize(byGroup, 3);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate finance Excel report", e);
        }
    }

    public byte[] exportSubscriptions(SubscriptionReportResponse data, LocalDate from, LocalDate to, boolean onlySuspicious) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet summary = workbook.createSheet("Сводка");
            int r = 0;
            r = writeSectionHeader(summary, r, "Параметры отчета", headerStyle);
            r = writeKv(summary, r, "Период от", from);
            r = writeKv(summary, r, "Период до", to);
            r = writeKv(summary, r, "Только подозрительные", onlySuspicious);
            r++;
            r = writeSectionHeader(summary, r, "Итоги", headerStyle);
            r = writeKv(summary, r, "Сумма", data.getTotalAmount());
            r = writeKv(summary, r, "Количество", data.getTotalCount());
            r = writeKv(summary, r, "Подозрительных", data.getSuspiciousCount());
            autoSize(summary, 2);

            Sheet rows = workbook.createSheet("Абонементы");
            writeHeader(rows, 0, headerStyle,
                    "Subscription ID", "Student ID", "Ученик", "Услуга/Группа", "Сумма", "Статус",
                    "Suspicious", "Причина", "Дата создания", "Дата старта", "Всего занятий", "Осталось", "Посещений");
            int rr = 1;
            for (SubscriptionReportResponse.SubscriptionRowDto row : safeList(data.getRows())) {
                Row x = rows.createRow(rr++);
                setCell(x, 0, row.getSubscriptionId());
                setCell(x, 1, row.getStudentId());
                setCell(x, 2, row.getStudentName());
                setCell(x, 3, row.getServiceName());
                setCell(x, 4, row.getAmount());
                setCell(x, 5, row.getStatus());
                setCell(x, 6, row.isSuspicious());
                setCell(x, 7, row.getSuspiciousReason());
                setCell(x, 8, row.getCreatedDate());
                setCell(x, 9, row.getStartDate());
                setCell(x, 10, row.getTotalLessons());
                setCell(x, 11, row.getLessonsLeft());
                setCell(x, 12, row.getAttendanceCount());
            }
            autoSize(rows, 13);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate subscriptions Excel report", e);
        }
    }

    public byte[] exportTeachers(TeacherAnalyticsResponse data, LocalDate from, LocalDate to) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet summary = workbook.createSheet("Сводка");
            int r = 0;
            r = writeSectionHeader(summary, r, "Параметры отчета", headerStyle);
            r = writeKv(summary, r, "Период от", from);
            r = writeKv(summary, r, "Период до", to);
            if (data.getTopEmployee() != null) {
                TopEmployeeDto te = data.getTopEmployee();
                r++;
                r = writeSectionHeader(summary, r, "Лучший преподаватель", headerStyle);
                r = writeKv(summary, r, "ФИО", te.getFullName());
                r = writeKv(summary, r, "Индекс", te.getIndex());
                r = writeKv(summary, r, "Выручка", te.getRevenue());
                r = writeKv(summary, r, "Загрузка (%)", te.getGroupLoadRate());
                r = writeKv(summary, r, "Активные студенты", te.getActiveStudents());
            }
            autoSize(summary, 2);

            Sheet rows = workbook.createSheet("Преподаватели");
            writeHeader(rows, 0, headerStyle,
                    "Staff ID", "ФИО", "Активные", "Продано абонементов", "Студентов в период", "Выручка",
                    "Δ выручки (%)", "Всего студентов", "Средний tenure (мес)", "Суммарный tenure (мес)",
                    "Загрузка (%)", "Индекс");
            int rr = 1;
            for (TeacherAnalyticsResponse.TeacherRowDto row : safeList(data.getRows())) {
                Row x = rows.createRow(rr++);
                setCell(x, 0, row.getStaffId());
                setCell(x, 1, row.getFullName());
                setCell(x, 2, row.getActiveStudents());
                setCell(x, 3, row.getSubscriptionsSold());
                setCell(x, 4, row.getStudentsInPeriod());
                setCell(x, 5, row.getRevenue());
                setCell(x, 6, row.getRevenueDeltaPct());
                setCell(x, 7, row.getTotalStudents());
                setCell(x, 8, row.getAvgTenureMonths());
                setCell(x, 9, row.getTotalTenureMonths());
                setCell(x, 10, row.getGroupLoadRate());
                setCell(x, 11, row.getIndex());
            }
            autoSize(rows, 12);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate teachers Excel report", e);
        }
    }

    public byte[] exportRetention(RetentionResponse data, LocalDate from, LocalDate to, String cohortType) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet summary = workbook.createSheet("Сводка");
            int r = 0;
            r = writeSectionHeader(summary, r, "Параметры отчета", headerStyle);
            r = writeKv(summary, r, "Период от", from);
            r = writeKv(summary, r, "Период до", to);
            r = writeKv(summary, r, "Тип когорты", cohortType);
            autoSize(summary, 2);

            Sheet cohorts = workbook.createSheet("Когорты");
            writeHeader(cohorts, 0, headerStyle, "Ключ", "Когорта", "Размер", "M0", "M1", "M2", "M3", "M4", "M5");
            int cr = 1;
            for (RetentionResponse.CohortRowDto row : safeList(data.getCohorts())) {
                Row x = cohorts.createRow(cr++);
                setCell(x, 0, row.getCohortKey());
                setCell(x, 1, row.getCohort());
                setCell(x, 2, row.getSize());
                setCell(x, 3, row.getM0());
                setCell(x, 4, row.getM1());
                setCell(x, 5, row.getM2());
                setCell(x, 6, row.getM3());
                setCell(x, 7, row.getM4());
                setCell(x, 8, row.getM5());
            }
            autoSize(cohorts, 9);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate retention Excel report", e);
        }
    }

    public byte[] exportGroupLoad(GroupLoadResponse data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet rows = workbook.createSheet("Загрузка_групп");
            writeHeader(rows, 0, headerStyle, "Group ID", "Группа", "Студентов", "Вместимость", "Загрузка (%)");
            int rr = 1;
            for (GroupLoadResponse.GroupLoadRowDto row : safeList(data.getRows())) {
                Row x = rows.createRow(rr++);
                setCell(x, 0, row.getGroupId());
                setCell(x, 1, row.getGroupName());
                setCell(x, 2, row.getStudentsCount());
                setCell(x, 3, row.getCapacity());
                setCell(x, 4, row.getLoadPct());
            }
            autoSize(rows, 5);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate group load Excel report", e);
        }
    }

    public byte[] exportRoomLoad(RoomLoadResponse data, LocalDate from, LocalDate to, LocalDate timelineDate) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet summary = workbook.createSheet("Сводка");
            int r = 0;
            r = writeSectionHeader(summary, r, "Параметры отчета", headerStyle);
            r = writeKv(summary, r, "Период от", from);
            r = writeKv(summary, r, "Период до", to);
            r = writeKv(summary, r, "Дата таймлайна", timelineDate);
            autoSize(summary, 2);

            Sheet load = workbook.createSheet("Загрузка_аудиторий");
            writeHeader(load, 0, headerStyle,
                    "Room ID", "Аудитория", "Занятий", "Всего студентов", "Суммарная вместимость", "Загрузка (%)");
            int lr = 1;
            for (RoomLoadResponse.RoomLoadRowDto row : safeList(data.getRows())) {
                Row x = load.createRow(lr++);
                setCell(x, 0, row.getRoomId());
                setCell(x, 1, row.getRoomName());
                setCell(x, 2, row.getLessonsCount());
                setCell(x, 3, row.getTotalStudents());
                setCell(x, 4, row.getTotalCapacity());
                setCell(x, 5, row.getLoadPct());
            }
            autoSize(load, 6);

            Sheet timeline = workbook.createSheet("Таймлайн");
            writeHeader(timeline, 0, headerStyle, "Room ID", "Аудитория", "Занятость дня (%)");
            int tr = 1;
            for (RoomLoadResponse.RoomTimelineDto row : safeList(data.getTimeline())) {
                Row x = timeline.createRow(tr++);
                setCell(x, 0, row.getRoomId());
                setCell(x, 1, row.getRoomName());
                setCell(x, 2, row.getOccupancyPct());
            }
            autoSize(timeline, 3);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate room load Excel report", e);
        }
    }

    public byte[] exportGroupAttendance(GroupAttendanceResponse data, LocalDate from, LocalDate to) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet summary = workbook.createSheet("Сводка");
            int r = 0;
            r = writeSectionHeader(summary, r, "Параметры отчета", headerStyle);
            r = writeKv(summary, r, "Group ID", data.getGroupId());
            r = writeKv(summary, r, "Группа", data.getGroupName());
            r = writeKv(summary, r, "Период от", from);
            r = writeKv(summary, r, "Период до", to);
            r = writeKv(summary, r, "Средняя посещаемость (%)", data.getAvgAttendanceRate());
            autoSize(summary, 2);

            Sheet monthly = workbook.createSheet("По_месяцам");
            writeHeader(monthly, 0, headerStyle, "Месяц", "Посещаемость (%)");
            int mr = 1;
            for (MonthlyAttendanceDto row : safeList(data.getMonthly())) {
                Row x = monthly.createRow(mr++);
                setCell(x, 0, row.getMonth());
                setCell(x, 1, row.getRate());
            }
            autoSize(monthly, 2);

            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate group attendance Excel report", e);
        }
    }

    private void writeCategorySheet(Workbook workbook, CellStyle headerStyle, String sheetName, List<CategoryAmountDto> rows) {
        Sheet sheet = workbook.createSheet(sheetName);
        writeHeader(sheet, 0, headerStyle, "Категория", "Сумма");
        int r = 1;
        for (CategoryAmountDto row : rows) {
            Row x = sheet.createRow(r++);
            setCell(x, 0, row.getCategory());
            setCell(x, 1, row.getAmount());
        }
        autoSize(sheet, 2);
    }

    private int writeSectionHeader(Sheet sheet, int rowIndex, String title, CellStyle headerStyle) {
        Row row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(headerStyle);
        return rowIndex + 1;
    }

    private int writeKv(Sheet sheet, int rowIndex, String key, Object value) {
        Row row = sheet.createRow(rowIndex);
        setCell(row, 0, key);
        setCell(row, 1, value);
        return rowIndex + 1;
    }

    private void writeHeader(Sheet sheet, int rowIndex, CellStyle headerStyle, String... headers) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        return style;
    }

    private void setCell(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        if (value instanceof BigDecimal bd) {
            cell.setCellValue(bd.doubleValue());
            return;
        }
        if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
            return;
        }
        if (value instanceof LocalDate d) {
            cell.setCellValue(d.toString());
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private <T> List<T> safeList(List<T> input) {
        return input == null ? new ArrayList<>() : input;
    }
}
