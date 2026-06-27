package com.attendance.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceAdminResponse {

    private Long userId;
    private String empName;
    private String roleName;
    private Long orgUnitId;
    private String orgUnitName;
}
