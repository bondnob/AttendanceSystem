package com.attendance.ledger.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveLedgerDetailRequest {

    @NotNull
    private Long id;
    private String stationPoint;
    private Integer sortNo;
}
