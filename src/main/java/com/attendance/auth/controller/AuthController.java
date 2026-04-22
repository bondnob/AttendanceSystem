package com.attendance.auth.controller;

import com.attendance.auth.dto.DashboardResponse;
import com.attendance.auth.dto.LoginRequest;
import com.attendance.auth.dto.LoginResponse;
import com.attendance.auth.dto.UserMessageResponse;
import com.attendance.auth.service.AuthService;
import com.attendance.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin
@Tag(name = "认证接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录", description = "校验账号密码，签发 JWT token，并将 roleCode 放入 token 返回给前端。后续请求的 Authorization 既支持 Bearer token，也支持直接传 token。")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("登录成功", authService.login(request));
    }

    @Operation(summary = "账号工作台", description = "返回当前账号的请假类型申请人数、本月已审批/未审批人数，以及最近信息提示。")
    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.success(authService.getDashboard());
    }

}
