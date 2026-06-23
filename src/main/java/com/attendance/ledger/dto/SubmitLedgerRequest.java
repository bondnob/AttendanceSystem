package com.attendance.ledger.dto;

import java.util.List;
import lombok.Data;

@Data
public class SubmitLedgerRequest {

    private String month;
    private String remark;
    private String changeDescription;
    private List<SaveLedgerDetailRequest> details;
}
