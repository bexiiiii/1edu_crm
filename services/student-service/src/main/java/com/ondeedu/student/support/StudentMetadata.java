package com.ondeedu.student.support;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentMetadata {

    @Builder.Default
    private List<String> additionalPhones = List.of();
    private Boolean stateOrderParticipant;
    private String loyalty;
}
