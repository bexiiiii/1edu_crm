package com.ondeedu.student.dto;

import com.ondeedu.student.entity.StudentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDto {

    private UUID id;
    private String firstName;
    private String lastName;
    private String middleName;
    private String fullName;
    private String email;
    private String phone;
    private LocalDate birthDate;
    private StudentStatus status;
    private String parentName;
    private String parentPhone;
    private String address;
    private String city;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
