package com.ondeedu.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StudentCreatedEvent extends BaseEvent {

    private UUID studentId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;

    public StudentCreatedEvent(String tenantId, String userId, UUID studentId,
                                String firstName, String lastName, String email, String phone) {
        super("STUDENT_CREATED", tenantId, userId);
        this.studentId = studentId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
    }
}