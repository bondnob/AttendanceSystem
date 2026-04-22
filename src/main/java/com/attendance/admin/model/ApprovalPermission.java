package com.attendance.admin.model;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ApprovalPermission {

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
