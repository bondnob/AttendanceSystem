package com.attendance.ledger.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class EmployeeBasicShare {

    private Long id;
    private Long orgUnitId;
    private Long sharedUserId;
    private LocalDateTime createdAt;
}
