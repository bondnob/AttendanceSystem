package com.attendance.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrgUnitRequest {



    @NotBlank(message = "组织名称不能为空")
    private String orgName;

    @NotBlank(message = "组织类型不能为空")
    private String orgType;


}
