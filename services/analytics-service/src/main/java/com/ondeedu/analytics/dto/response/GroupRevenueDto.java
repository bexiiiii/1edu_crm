package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class GroupRevenueDto {
    private UUID groupId;
    private String groupName;
    private BigDecimal revenue;
}
