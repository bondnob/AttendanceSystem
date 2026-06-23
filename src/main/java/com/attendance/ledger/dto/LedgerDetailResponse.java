package com.attendance.ledger.dto;

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

    private String birthDate;
    private Integer age;
    private String workType;
    private String identityType;
    private String categoryMajor;
    private String categoryMinor;
    private String laborShift;
    private String teamName;
    private String shiftCategory;
    private String stationPoint;
    private String shiftType;
    private String isTeamLeader;
    private Integer isNonWorking;
    private String nonWorkingReason;
    private Integer sortNo;
    private String jiaBan1;
    private String jiaBan2;
    private String yiBan1;
    private String yiBan2;
    private String bingBan1;
    private String bingBan2;
    private String dingBan1;
    private String dingBan2;
    private String yuBei1;
    private String yuBei2;
    private String dailyName;
    private String extraShiftJson;
}
