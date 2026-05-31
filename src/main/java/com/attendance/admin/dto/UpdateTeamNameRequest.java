package com.attendance.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateTeamNameRequest {

    @NotNull(message = "部门ID不能为空")
    private Long orgUnitId;

    @NotBlank(message = "班组名称不能为空")
    private String teamName;

    private String shiftCategory;

    private Integer sortNo;

    private Integer isEnabled;
}
