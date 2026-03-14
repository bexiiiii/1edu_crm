package com.ondeedu.student.dto;

import com.ondeedu.student.entity.Gender;
import com.ondeedu.student.entity.StudentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    private String customer;
    private String studentPhoto;
    private String email;
    private String phone;
    private LocalDate birthDate;
    private StudentStatus status;
    private String parentName;
    private String parentPhone;
    private String studentPhone;
    private Gender gender;
    private String address;
    private String city;
    private String school;
    private String grade;
    private String additionalInfo;
    private String contract;
    private String discount;
    private String comment;
    private Boolean stateOrderParticipant;
    private String loyalty;
    private List<String> additionalPhones;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
