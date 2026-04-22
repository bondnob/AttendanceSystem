package com.attendance.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateOrgUnitRequest {


    @NotBlank(message = "组织类型不能为空")
    private String orgType;

    @NotNull(message = "排序号不能为空")
    private Integer sortNo;

    @NotNull(message = "启用状态不能为空")
    private Integer isEnabled;
}
