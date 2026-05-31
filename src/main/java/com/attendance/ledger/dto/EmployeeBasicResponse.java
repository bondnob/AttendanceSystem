package com.attendance.ledger.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeBasicResponse {

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
    private String orgUnitName;
    private String teamName;
    private Integer isActive;
    private Integer isDistributed;
    private LocalDateTime distributedAt;
}
