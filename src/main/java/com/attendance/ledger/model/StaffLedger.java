package com.attendance.ledger.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class StaffLedger {

    private Long id;
    private Long orgUnitId;
    private String ledgerMonth;
    private String status;
    private Integer inWorkCount;
    private String remark;
    private String changeDescription;
    private Long directorUserId;
    private String directorOpinion;
    private LocalDateTime directorApprovedAt;
    private Long hrUserId;
    private String hrOpinion;
    private LocalDateTime hrApprovedAt;
    private String sharedUserIds;
    private LocalDateTime submittedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
