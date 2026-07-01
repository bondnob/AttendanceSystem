package com.attendance.ledger.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class ShareEmployeeBasicRequest {

    @NotEmpty(message = "请选择部门")
    private List<Long> orgUnitIds;

    @NotEmpty(message = "请选择共享领导")
    private List<Long> targetUserIds;
}
