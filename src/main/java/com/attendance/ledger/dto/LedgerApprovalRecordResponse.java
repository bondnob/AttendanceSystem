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
public class LedgerApprovalRecordResponse {

    private Long id;
    private String step;
    private String action;
    private String opinion;
    private Long operatorUserId;
    private String operatorName;
    private LocalDateTime createdAt;
}
