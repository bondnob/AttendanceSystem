package com.attendance.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrgUnitResponse {

    private Long id;
    private String orgCode;
    private String orgName;
    private String orgType;
    private Integer sortNo;
    private Integer isEnabled;
}
