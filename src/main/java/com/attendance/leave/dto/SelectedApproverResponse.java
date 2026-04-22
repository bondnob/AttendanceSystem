package com.attendance.leave.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SelectedApproverResponse {

    private Integer stepNo;
    private String stepName;
    private Long approverUserId;
    private String approverName;
    private String approverRoleCode;
    private String approverRoleName;
    private String candidateGroup;
}
