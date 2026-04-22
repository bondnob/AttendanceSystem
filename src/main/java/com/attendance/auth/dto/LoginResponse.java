package com.attendance.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private Long userId;
    private String username;
    private String roleCode;
    private String empName;
    private Long orgUnitId;
    private String dataScope;
    private String approvalScope;
    private String token;
    private String tokenType;
    private Long expiresInSeconds;
}
