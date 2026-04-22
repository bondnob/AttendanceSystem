package com.attendance.leave.model;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ApprovalRule {

    private Long id;
    private String ruleCode;
    private String ruleName;
    private String applicantType;
    private String positionLevelCode;
    private String leaveScope;
    private Integer exceedsMonthOnly;
    private BigDecimal minDays;
    private BigDecimal maxDays;
    private String description;
    private Integer isEnabled;
}
