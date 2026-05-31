package com.attendance.ledger.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class EmployeeBasic {

    private Long id;
    private String idCardNo;
    private String empName;
    private String gender;
    private String birthDate;
    private Integer age;
    private String workType;
    private String identityType;
    private String categoryMajor;
    private String categoryMinor;
    private String laborShift;
    private String isTeamLeader;
    private Long orgUnitId;
    private String teamName;
    private Integer isActive;
    private String uploadBatch;
    private Integer isDistributed;
    private LocalDateTime distributedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
