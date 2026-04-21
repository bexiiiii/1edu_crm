package com.ondeedu.student.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.student.dto.SaveStudentCallLogRequest;
import com.ondeedu.student.dto.StudentCallLogDto;
import com.ondeedu.student.service.StudentCallLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students/call-logs")
@RequiredArgsConstructor
@Tag(name = "Student Call Logs", description = "Журнал обзвонов студентов")
public class StudentCallLogController {

    private final StudentCallLogService callLogService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('MANAGER') or hasRole('RECEPTIONIST') or hasAuthority('STUDENTS_EDIT')")
    @Operation(summary = "Создать запись обзвона", description = "Добавляет новую запись обзвона студента")
    public ApiResponse<StudentCallLogDto> createCallLog(@Valid @RequestBody SaveStudentCallLogRequest request) {
        return ApiResponse.success(callLogService.createCallLog(request), "Запись обзвона создана");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('MANAGER') or hasRole('RECEPTIONIST') or hasAuthority('STUDENTS_EDIT')")
    @Operation(summary = "Изменить запись обзвона", description = "Изменяет запись обзвона. Требуется указать причину изменения.")
    public ApiResponse<StudentCallLogDto> updateCallLog(
            @PathVariable UUID id,
            @Valid @RequestBody SaveStudentCallLogRequest request) {
        return ApiResponse.success(callLogService.updateCallLog(id, request), "Запись обзвона обновлена");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "Удалить запись обзвона", description = "Удаляет запись обзвона. Требуется указать причину удаления. Доступно только админам.")
    public ApiResponse<Void> deleteCallLog(
            @PathVariable UUID id,
            @RequestParam String reason) {
        callLogService.deleteCallLog(id, reason);
        return ApiResponse.success("Запись обзвона удалена");
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('MANAGER') or hasRole('RECEPTIONIST') or hasRole('TEACHER') or hasAuthority('STUDENTS_VIEW')")
    @Operation(summary = "История обзвонов студента", description = "Возвращает все записи обзвонов конкретного студента")
    public ApiResponse<PageResponse<StudentCallLogDto>> getCallLogsByStudent(
            @PathVariable UUID studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(callLogService.getCallLogsByStudent(
                studentId, org.springframework.data.domain.PageRequest.of(page, size)));
    }

    @GetMapping("/student/{studentId}/range")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('MANAGER') or hasRole('RECEPTIONIST') or hasRole('TEACHER') or hasAuthority('STUDENTS_VIEW')")
    @Operation(summary = "История обзвонов студента за период", description = "Возвращает записи обзвонов студента за указанный период")
    public ApiResponse<PageResponse<StudentCallLogDto>> getCallLogsByStudentAndDateRange(
            @PathVariable UUID studentId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(callLogService.getCallLogsByStudentAndDateRange(
                studentId, fromDate, toDate, org.springframework.data.domain.PageRequest.of(page, size)));
    }

    @GetMapping("/caller/{staffId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('MANAGER')")
    @Operation(summary = "Обзвоны менеджера", description = "Возвращает все записи обзвонов конкретного менеджера. Только для админов.")
    public ApiResponse<PageResponse<StudentCallLogDto>> getCallLogsByCaller(
            @PathVariable UUID staffId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(callLogService.getCallLogsByCaller(
                staffId, org.springframework.data.domain.PageRequest.of(page, size)));
    }
}
