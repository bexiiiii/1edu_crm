package com.ondeedu.student.dto;

import com.ondeedu.student.entity.StudentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStudentRequest {

    @Size(max = 100, message = "First name must be less than 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must be less than 100 characters")
    private String lastName;

    @Size(max = 100, message = "Middle name must be less than 100 characters")
    private String middleName;

    @Email(message = "Invalid email format")
    private String email;

    @Size(max = 20, message = "Phone must be less than 20 characters")
    private String phone;

    private LocalDate birthDate;

    private StudentStatus status;

    @Size(max = 200, message = "Parent name must be less than 200 characters")
    private String parentName;

    @Size(max = 20, message = "Parent phone must be less than 20 characters")
    private String parentPhone;

    @Size(max = 500, message = "Address must be less than 500 characters")
    private String address;

    @Size(max = 100, message = "City must be less than 100 characters")
    private String city;

    private String notes;
}