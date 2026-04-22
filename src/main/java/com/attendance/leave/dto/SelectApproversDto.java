package com.attendance.leave.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class SelectApproversDto {

    @NotEmpty(message = "请选择审批人")
    private List<Long> approverUserIds;

    private String comment;
}
