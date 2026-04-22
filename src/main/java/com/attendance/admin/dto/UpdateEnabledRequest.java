package com.attendance.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateEnabledRequest {

    @NotNull(message = "启用状态不能为空")
    private Integer isEnabled;
}
