package com.attendance.leave.model;

import lombok.Data;

@Data
public class UserAccount {

    private Long id;
    private String username;
    private String passwordHash;
    private String roleCode;
    private String roleName;
    private String empName;
    private String idCardNo;
    private String teamName;
    private String workType;
    private String jobTitle;
    private String displayName;
    private String applicantType;
    private String positionLevelCode;
    private String leaderGroupCode;
    private Long orgUnitId;
    private String dataScope;
    private String approvalScope;
    private Integer isEnabled;
}
