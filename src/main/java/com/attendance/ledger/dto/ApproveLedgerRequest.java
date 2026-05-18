package com.attendance.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApproveLedgerRequest {

    @NotBlank
    private String action;
    private String opinion;
}
