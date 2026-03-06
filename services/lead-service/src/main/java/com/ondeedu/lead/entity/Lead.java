package com.ondeedu.lead.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "leads")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lead extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 30)
    @Builder.Default
    private LeadStage stage = LeadStage.NEW;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "course_interest", length = 255)
    private String courseInterest;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "assigned_to")
    private String assignedTo;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
