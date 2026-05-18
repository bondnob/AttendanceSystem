package com.attendance.ledger.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerDetailResponse {

    private Long id;
    private Long employeeBasicId;
    private String idCardNo;
    private String empName;
    private String gender;
    private LocalDate birthDate;
    private Integer age;
    private String workType;
    private String identityType;
    private String categoryMajor;
    private String categoryMinor;
    private String laborShift;
    private String teamName;
    private String stationPoint;
    private String shiftType;
    private Integer isTeamLeader;
    private Integer isNonWorking;
    private String nonWorkingReason;
    private Integer sortNo;
}
