package com.attendance.ledger.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class ShareLedgerRequest {

    @NotEmpty(message = "请选择台账")
    private List<Long> ledgerIds;

    @NotEmpty(message = "请选择共享领导")
    private List<Long> targetUserIds;
}
