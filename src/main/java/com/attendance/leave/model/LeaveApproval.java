package com.attendance.leave.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LeaveApproval {

    private Long id;
    private Long leaveRequestId;
    private Long ruleStepId;
    private Integer stepNo;
    private String actionType;
    private String stepName;
    private String approverRoleCode;
    private Long approverUserId;
    private String approvalStatus;
    private String approvalComment;
    private String signatureUrl;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
