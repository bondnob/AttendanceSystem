package com.attendance.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    @NotBlank(message = "姓名不能为空")
    private String empName;

    @NotBlank(message = "申请人类型不能为空")
    private String applicantType;

    private String leaderGroupCode;

    @NotNull(message = "组织不能为空")
    private Long orgUnitId;

    private String dataScope = "SELF";

    private String approvalScope = "NONE";

    private Integer isEnabled = 1;
}
