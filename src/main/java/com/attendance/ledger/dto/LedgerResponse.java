package com.attendance.ledger.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerResponse {

    private Long id;
    private Long orgUnitId;
    private String orgUnitName;
    private String ledgerMonth;
    private String status;
    private Integer inWorkCount;
    private String remark;
    private String changeDescription;
    private Long directorUserId;
    private String directorName;
    private String directorOpinion;
    private LocalDateTime directorApprovedAt;
    private Long hrUserId;
    private String hrName;
    private String hrOpinion;
    private LocalDateTime hrApprovedAt;
    private LocalDateTime submittedAt;
    private Long createdBy;
    private String creatorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<LedgerDetailResponse> details;
    private List<LedgerDetailResponse> nonWorkingDetails;
    private List<LedgerApprovalRecordResponse> approvalRecords;
    private Map<String, String> config;
}
