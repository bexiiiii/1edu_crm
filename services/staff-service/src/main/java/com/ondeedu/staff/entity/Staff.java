package com.ondeedu.staff.entity;

import com.ondeedu.common.payroll.SalaryType;
import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "staff")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Staff extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "image", nullable = false)
    private String image;

    @Column(name = "birthdate", nullable = false)
    private LocalDate birthdate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    @Builder.Default
    private Gender gender = Gender.MALE;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "comments", nullable = false)
    private String comments;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type" , nullable = true)
    private DocumentType documentType = DocumentType.ID_CARD;

    @Column(name = "document_number" , nullable = true)
    private String documentNumber;

    @Column(name = "document_given_date", nullable = true)
    private LocalDate documentGivenDate;

    @Column(name = "issued_by", nullable = true)
    private String issuedBy;

    @Column(name = "document_file" , nullable = true)
    private String documentFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private StaffRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StaffStatus status = StaffStatus.ACTIVE;

    //Дополнительно
    @Column(name = "position", length = 200)
    private String position;

    @Column(name = "iin", nullable = true)
    private String iin;

    @Column(name = "order_number", nullable = true)
    private String orderNumber;

    @Column(name = "contract", nullable = true)
    private String contract;

    @Column(name = "contract_date", nullable = true)
    private LocalDate contractDate;

    @Column(name = "probation_period", nullable = true)
    private String probationPeriod;

    @Column(name = "probation_period_comments", nullable = true)
    private String probationPeriodComments;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "end_hire_date")
    private LocalDate endHireDate;

    @Column(name = "salary", precision = 15, scale = 2)
    private BigDecimal salary;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_type", length = 30, nullable = false)
    @Builder.Default
    private SalaryType salaryType = SalaryType.FIXED;

    @Column(name = "salary_percentage", precision = 5, scale = 2)
    private BigDecimal salaryPercentage;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public String getFullName() {
        if (middleName != null && !middleName.isBlank()) {
            return firstName + " " + middleName + " " + lastName;
        }
        return firstName + " " + lastName;
    }
}
