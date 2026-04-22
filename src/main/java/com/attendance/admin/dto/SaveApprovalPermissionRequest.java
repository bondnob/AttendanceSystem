package com.attendance.admin.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class SaveApprovalPermissionRequest {

    private Long id;
    private Long orgUnitId;
    private String roleCode;
    private String applicantType;
    private String positionLevelCode;
    private String leaveScope;
    private BigDecimal minDays;
    private BigDecimal maxDays;
    private Integer isEnabled;
}
