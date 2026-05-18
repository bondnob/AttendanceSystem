package com.attendance.ledger.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LedgerConfig {

    private Long id;
    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime updatedAt;
}
