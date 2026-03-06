package com.ondeedu.student.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "students")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Student extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "Customer", nullable = true)
    private String customer;

    @Column(name = "student_photo", nullable = true)
    private String studentPhoto;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;


    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private StudentStatus status = StudentStatus.ACTIVE;

    @Column(name = "parent_name", length = 200)
    private String parentName;

    @Column(name = "parent_phone", length = 20)
    private String parentPhone;

    @Column(name = "student_phone", length = 20)
    private String studentPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    @Builder.Default
    private Gender gender = Gender.MALE;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name="school", nullable = true)
    private String school;

    @Column(name = "grade", nullable = false)
    private String grade;

    @Column(name= "additional_info")
    private String additionalInfo;

    @Column(name= "contract")
    private String contract;

    @Column(name = "discount")
    private String discount;

    @Column(name= "comment")
    private String comment;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    public String getFullName() {
        if (middleName != null && !middleName.isBlank()) {
            return firstName + " " + middleName + " " + lastName;
        }
        return firstName + " " + lastName;
    }
}
