package com.attendance.auth.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardResponse {

    private List<DashboardLeaveTypeCountResponse> leaveTypeRequestCounts;
    private DashboardApprovalStatsResponse monthlyApprovalStats;
    private List<UserMessageResponse> messages;
}
