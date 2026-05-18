package com.attendance.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LedgerConfigRequest {

    @NotBlank
    private String configKey;
    private String configValue;
}
