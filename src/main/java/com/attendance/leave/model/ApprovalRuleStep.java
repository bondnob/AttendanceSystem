package com.attendance.leave.model;

import lombok.Data;

@Data
public class ApprovalRuleStep {

    private Long id;
    private Long ruleId;
    private Integer stepNo;
    private String actionType;
    private Integer assigneeCount;
    private String candidateGroup;
    private String stepCode;
    private String stepCodeName;
    private String stepName;
    private String approverSource;
    private String approverRoleCode;
    private String approverRoleName;
    private Integer returnToOrg;
}
