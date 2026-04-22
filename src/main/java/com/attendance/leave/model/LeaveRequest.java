package com.attendance.leave.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LeaveRequest {

    private Long id;
    private String requestNo;
    private Long applicantId;
    private Long orgUnitId;
    private Long leaveTypeId;
    private Long approvalRuleId;
    private String applicantNameSnapshot;
    private String applicantType;
    private String positionLevelCode;
    private String jobTitleSnapshot;
    private String teamLeaderSnapshot;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal leaveDays;
    private String reason;
    private String remark;
    private String status;
    private Integer currentStep;
    private String currentActionType;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private LocalDateTime finalApprovedAt;
    private Long createdBy;
    private java.math.BigDecimal allowedDays;
    private Integer exceedsOneMonth;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
