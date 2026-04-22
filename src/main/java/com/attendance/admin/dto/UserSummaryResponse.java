package com.attendance.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSummaryResponse {

    private Long id;
    private String username;
    private String roleCode;
    private String roleName;
    private String empName;
    private Long orgUnitId;
    private String jobTitle;
    private String applicantType;
    private String positionLevelCode;
    private String leaderGroupCode;
    private String dataScope;
    private String approvalScope;
    private Integer isEnabled;
}
