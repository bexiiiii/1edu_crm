package com.ondeedu.lead.search;

import com.ondeedu.lead.dto.LeadDto;
import com.ondeedu.lead.entity.Lead;
import com.ondeedu.lead.entity.LeadStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "leads")
public class LeadSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Text)
    private String firstName;

    @Field(type = FieldType.Text)
    private String lastName;

    @Field(type = FieldType.Text)
    private String fullName;

    @Field(type = FieldType.Text)
    private String phone;

    @Field(type = FieldType.Text)
    private String email;

    @Field(type = FieldType.Keyword)
    private LeadStage stage;

    @Field(type = FieldType.Text)
    private String source;

    @Field(type = FieldType.Text)
    private String courseInterest;

    @Field(type = FieldType.Text)
    private String notes;

    @Field(type = FieldType.Keyword)
    private String assignedTo;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_fraction)
    private Instant createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_fraction)
    private Instant updatedAt;

    public static LeadSearchDocument from(Lead lead, String tenantId) {
        return LeadSearchDocument.builder()
                .id(lead.getId().toString())
                .tenantId(tenantId)
                .firstName(lead.getFirstName())
                .lastName(lead.getLastName())
                .fullName(lead.getFullName())
                .phone(lead.getPhone())
                .email(lead.getEmail())
                .stage(lead.getStage())
                .source(lead.getSource())
                .courseInterest(lead.getCourseInterest())
                .notes(lead.getNotes())
                .assignedTo(lead.getAssignedTo())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .build();
    }

    public LeadDto toDto() {
        return LeadDto.builder()
                .id(id != null ? java.util.UUID.fromString(id) : null)
                .firstName(firstName)
                .lastName(lastName)
                .fullName(fullName)
                .phone(phone)
                .email(email)
                .stage(stage)
                .source(source)
                .courseInterest(courseInterest)
                .notes(notes)
                .assignedTo(assignedTo)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
