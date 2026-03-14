package com.ondeedu.student.dto;

import com.ondeedu.student.entity.Gender;
import com.ondeedu.student.entity.StudentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStudentRequest {

    @Size(max = 255, message = "Full name must be less than 255 characters")
    private String fullName;

    @Size(max = 100, message = "First name must be less than 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must be less than 100 characters")
    private String lastName;

    @Size(max = 100, message = "Middle name must be less than 100 characters")
    private String middleName;

    private StudentStatus status;

    @Size(max = 255, message = "Customer must be less than 255 characters")
    private String customer;

    @Size(max = 500, message = "Student photo URL must be less than 500 characters")
    private String studentPhoto;

    @Email(message = "Invalid email format")
    private String email;

    @Size(max = 20, message = "Phone must be less than 20 characters")
    private String phone;

    private LocalDate birthDate;

    @Size(max = 200, message = "Parent name must be less than 200 characters")
    private String parentName;

    @Size(max = 20, message = "Parent phone must be less than 20 characters")
    private String parentPhone;

    @Size(max = 20, message = "Student phone must be less than 20 characters")
    private String studentPhone;

    private Gender gender;

    @Size(max = 500, message = "Address must be less than 500 characters")
    private String address;

    @Size(max = 100, message = "City must be less than 100 characters")
    private String city;

    @Size(max = 255, message = "School must be less than 255 characters")
    private String school;

    @Size(max = 255, message = "Grade must be less than 255 characters")
    private String grade;

    private String additionalInfo;

    @Size(max = 500, message = "Contract must be less than 500 characters")
    private String contract;

    @Size(max = 255, message = "Discount must be less than 255 characters")
    private String discount;

    private String comment;

    private Boolean stateOrderParticipant;

    @Size(max = 100, message = "Loyalty must be less than 100 characters")
    private String loyalty;

    private List<String> additionalPhones;

    private String notes;
}
