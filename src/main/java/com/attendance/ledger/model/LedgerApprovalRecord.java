package com.attendance.ledger.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LedgerApprovalRecord {

    private Long id;
    private Long ledgerId;
    private String step;
    private String action;
    private String opinion;
    private Long operatorUserId;
    private LocalDateTime createdAt;
}
