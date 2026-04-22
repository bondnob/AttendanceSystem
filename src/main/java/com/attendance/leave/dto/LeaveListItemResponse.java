package com.attendance.leave.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeaveListItemResponse {

    private Long id;
    private String requestNo;
    private String status;
    private Integer currentStep;
    private String currentActionType;
    private Long applicantId;
    private String applicantName;
    private String applicantType;
    private Long orgUnitId;
    private Long leaveTypeId;
    private String leaveTypeName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal leaveDays;
    private String teamLeaderSnapshot;
    private String reason;
    private String remark;
    private LocalDateTime submittedAt;
}
