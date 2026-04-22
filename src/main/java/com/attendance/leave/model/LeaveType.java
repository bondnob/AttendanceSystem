package com.attendance.leave.model;

import lombok.Data;

@Data
public class LeaveType {

    private Long id;
    private String leaveCode;
    private String leaveName;
    private Double defaultDays;
    private String dayUnit;
    private String calcRule;
    private String description;
}
