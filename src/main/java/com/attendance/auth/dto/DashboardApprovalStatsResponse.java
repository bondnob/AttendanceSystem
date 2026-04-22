package com.attendance.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardApprovalStatsResponse {

    private Long approvedCount;
    private Long pendingCount;
}
