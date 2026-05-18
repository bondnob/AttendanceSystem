package com.attendance.ledger.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StaffLedgerDetail {

    private Long id;
    private Long ledgerId;
    private Long employeeBasicId;
    private String stationPoint;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
