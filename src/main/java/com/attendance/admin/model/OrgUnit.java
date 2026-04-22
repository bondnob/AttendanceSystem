package com.attendance.admin.model;

import lombok.Data;

@Data
public class OrgUnit {

    private Long id;
    private String orgCode;
    private String orgName;
    private String orgType;
    private Integer sortNo;
    private Integer isEnabled;
}
