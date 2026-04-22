package com.attendance.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveLeaveSignRequirementRequest {

    private Long id;

    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    @NotNull(message = "假别不能为空")
    private Long leaveTypeId;

    @NotNull(message = "是否需要签字不能为空")
    private Integer signRequired;

    private Integer isEnabled;
}
