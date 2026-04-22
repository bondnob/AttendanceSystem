package com.attendance.leave.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchApproveLeaveResponse {

    private Integer approvedCount;
    private List<LeaveDetailResponse> records;
}
