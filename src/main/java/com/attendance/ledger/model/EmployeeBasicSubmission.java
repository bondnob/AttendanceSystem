package com.attendance.ledger.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class EmployeeBasicSubmission {

    private Long id;
    private Long orgUnitId;
    private String status;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
