package com.attendance.ledger.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateEmployeeBasicRequest {

    @NotNull
    private Long id;

    private String workType;
    private String teamName;
    private String laborShift;
    private String isTeamLeader;
}
