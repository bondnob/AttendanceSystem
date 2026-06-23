package com.attendance.ledger.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class SaveLedgerRequest {

    private String remark;
    private String changeDescription;

    @NotNull
    private List<SaveLedgerDetailRequest> details;
}
