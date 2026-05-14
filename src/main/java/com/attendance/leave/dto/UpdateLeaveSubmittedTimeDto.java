package com.attendance.leave.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UpdateLeaveSubmittedTimeDto {

    @NotNull(message = "申请时间不能为空")
    private LocalDateTime submittedAt;
}
