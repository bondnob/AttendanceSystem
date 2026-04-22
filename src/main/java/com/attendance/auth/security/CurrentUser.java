package com.attendance.auth.security;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CurrentUser {

    private Long userId;
    private String username;
    private String roleCode;
    private Long orgUnitId;
}
