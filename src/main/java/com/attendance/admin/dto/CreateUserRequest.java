package com.attendance.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

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
}
