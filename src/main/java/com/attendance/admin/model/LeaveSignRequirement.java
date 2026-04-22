package com.attendance.admin.model;

import lombok.Data;

@Data
public class LeaveSignRequirement {

    private Long id;
    private String roleCode;
    private Long leaveTypeId;
    private Integer signRequired;
    private Integer isEnabled;
}
