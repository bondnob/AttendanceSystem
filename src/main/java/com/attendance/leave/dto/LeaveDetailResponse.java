package com.attendance.leave.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeaveDetailResponse {

    private Long id;
    private String requestNo;
    private String status;
    private Integer currentStep;
    private String currentActionType;
    private Long approvalRuleId;
    private Long applicantId;
    private String applicantName;
    private String applicantType;
    private String positionLevelCode;
    private String jobTitleSnapshot;
    private String teamLeaderSnapshot;
    private Long orgUnitId;
    private Long leaveTypeId;
    private String leaveTypeName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal leaveDays;
    private BigDecimal allowedDays;
    private Integer exceedsOneMonth;
    private String reason;
    private String remark;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private LocalDateTime finalApprovedAt;
    private String pdfUrl;
    private List<ApprovalRecordResponse> approvals;
}
