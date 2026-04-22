package com.attendance.leave.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveLeaveRequestDto {

    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    private String comment;
}
