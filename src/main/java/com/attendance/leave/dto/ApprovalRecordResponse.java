package com.attendance.leave.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalRecordResponse {

    private Integer stepNo;
    private String actionType;
    private String stepName;
    private String candidateGroup;
    private String approverRoleName;
    private String approverRoleCode;
    private Long approverUserId;
    private String approverName;
    private String approvalStatus;
    private String approvalComment;
    private String signatureUrl;
    private LocalDateTime approvedAt;
}
