package com.attendance.ledger.dto;

import lombok.Data;

@Data
public class SaveLedgerDetailRequest {

    private Long id;
    private Long employeeBasicId;
    private String stationPoint;
    private String teamName;
    private String shiftCategory;
    private String workType;
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
    private String yuBei3;
    private String yuBei4;
    private String dailyName;
    private String identityType;
    private String extraShiftJson;
}
