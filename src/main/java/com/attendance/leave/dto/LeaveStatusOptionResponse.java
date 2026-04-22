package com.attendance.leave.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeaveStatusOptionResponse {

    private String code;
    private String name;
}
