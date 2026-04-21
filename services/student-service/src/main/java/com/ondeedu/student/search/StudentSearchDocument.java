package com.ondeedu.student.search;

import com.ondeedu.student.dto.StudentDto;
import com.ondeedu.student.entity.Gender;
import com.ondeedu.student.entity.Student;
import com.ondeedu.student.entity.StudentStatus;
import com.ondeedu.student.support.StudentMetadata;
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
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "students")
public class StudentSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Keyword)
    private String branchId;

    @Field(type = FieldType.Text)
    private String firstName;

    @Field(type = FieldType.Text)
    private String lastName;

    @Field(type = FieldType.Text)
    private String middleName;

    @Field(type = FieldType.Text)
    private String fullName;

    @Field(type = FieldType.Text)
    private String customer;

    @Field(type = FieldType.Text)
    private String studentPhoto;

    @Field(type = FieldType.Text)
    private String email;

    @Field(type = FieldType.Text)
    private String phone;

    @Field(type = FieldType.Date, format = DateFormat.date)
    private LocalDate birthDate;

    @Field(type = FieldType.Keyword)
    private StudentStatus status;

    @Field(type = FieldType.Text)
    private String parentName;

    @Field(type = FieldType.Text)
    private String parentPhone;

    @Field(type = FieldType.Text)
    private String studentPhone;

    @Field(type = FieldType.Keyword)
    private Gender gender;

    @Field(type = FieldType.Text)
    private String address;

    @Field(type = FieldType.Text)
    private String city;

    @Field(type = FieldType.Text)
    private String school;

    @Field(type = FieldType.Text)
    private String grade;

    @Field(type = FieldType.Text)
    private String additionalInfo;

    @Field(type = FieldType.Text)
    private String contract;

    @Field(type = FieldType.Text)
    private String discount;

    @Field(type = FieldType.Text)
    private String comment;

    @Field(type = FieldType.Text)
    private String notes;

    @Field(type = FieldType.Keyword)
    private List<String> additionalPhones;

    @Field(type = FieldType.Boolean)
    private Boolean stateOrderParticipant;

    @Field(type = FieldType.Keyword)
    private String loyalty;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_fraction)
    private Instant createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_fraction)
    private Instant updatedAt;

    public static StudentSearchDocument from(Student student, String tenantId, StudentMetadata metadata) {
        return StudentSearchDocument.builder()
                .id(student.getId().toString())
                .tenantId(tenantId)
                .branchId(student.getBranchId() != null ? student.getBranchId().toString() : null)
                .firstName(student.getFirstName())
                .lastName(student.getLastName())
                .middleName(student.getMiddleName())
                .fullName(student.getFullName())
                .customer(student.getCustomer())
                .studentPhoto(student.getStudentPhoto())
                .email(student.getEmail())
                .phone(student.getPhone())
                .birthDate(student.getBirthDate())
                .status(student.getStatus())
                .parentName(student.getParentName())
                .parentPhone(student.getParentPhone())
                .studentPhone(student.getStudentPhone())
                .gender(student.getGender())
                .address(student.getAddress())
                .city(student.getCity())
                .school(student.getSchool())
                .grade(student.getGrade())
                .additionalInfo(student.getAdditionalInfo())
                .contract(student.getContract())
                .discount(student.getDiscount())
                .comment(student.getComment())
                .notes(student.getNotes())
                .additionalPhones(metadata.getAdditionalPhones())
                .stateOrderParticipant(metadata.getStateOrderParticipant())
                .loyalty(metadata.getLoyalty())
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();
    }

    public StudentDto toDto() {
        return StudentDto.builder()
                .id(id != null ? java.util.UUID.fromString(id) : null)
                .firstName(firstName)
                .lastName(lastName)
                .middleName(middleName)
                .fullName(fullName)
                .customer(customer)
                .studentPhoto(studentPhoto)
                .email(email)
                .phone(phone)
                .birthDate(birthDate)
                .status(status)
                .parentName(parentName)
                .parentPhone(parentPhone)
                .studentPhone(studentPhone)
                .gender(gender)
                .address(address)
                .city(city)
                .school(school)
                .grade(grade)
                .additionalInfo(additionalInfo)
                .contract(contract)
                .discount(discount)
                .comment(comment)
                .notes(notes)
                .additionalPhones(additionalPhones)
                .stateOrderParticipant(stateOrderParticipant)
                .loyalty(loyalty)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
