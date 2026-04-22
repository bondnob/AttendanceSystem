package com.attendance.auth.dto;

import lombok.Data;

@Data
public class DashboardLeaveTypeCountResponse {

    private Long leaveTypeId;
    private String leaveTypeName;
    private Long requestCount;
}
