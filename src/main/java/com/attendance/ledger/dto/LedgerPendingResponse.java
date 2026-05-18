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
public class LedgerPendingResponse {

    private Long id;
    private Long orgUnitId;
    private String orgUnitName;
    private String ledgerMonth;
    private String status;
    private Integer inWorkCount;
    private String creatorName;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
}
