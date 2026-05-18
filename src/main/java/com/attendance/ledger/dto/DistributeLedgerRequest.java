package com.attendance.ledger.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class DistributeLedgerRequest {

    @NotEmpty
    private List<Long> userIds;
}
