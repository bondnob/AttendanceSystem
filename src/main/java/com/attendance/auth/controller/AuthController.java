package com.attendance.auth.controller;

import com.attendance.auth.dto.DashboardResponse;
import com.attendance.auth.dto.LoginRequest;
import com.attendance.auth.dto.LoginResponse;
import com.attendance.auth.dto.DashboardApprovalStatsResponse;
import com.attendance.auth.dto.DashboardLeaveTypeCountResponse;
import com.attendance.auth.dto.UserMessageResponse;
import com.attendance.auth.service.AuthService;
import com.attendance.common.ApiResponse;
import com.attendance.common.PageResponse;
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

    @Operation(summary = "账号工作台-请假类别统计", description = "返回当前账号按数据权限统计的当月请假类别及数量，按请假开始时间所属月份过滤。")
    @GetMapping("/dashboard/leave-type-request-counts")
    public ApiResponse<List<DashboardLeaveTypeCountResponse>> dashboardLeaveTypeRequestCounts() {
        return ApiResponse.success(authService.getDashboardLeaveTypeRequestCounts());
    }

    @Operation(summary = "账号工作台-待审批已审批统计", description = "返回当前账号当月待审批、已审批数量，按请假开始时间所属月份过滤。")
    @GetMapping("/dashboard/approval-stats")
    public ApiResponse<DashboardApprovalStatsResponse> dashboardApprovalStats() {
        return ApiResponse.success(authService.getDashboardApprovalStats());
    }

    @Operation(summary = "账号工作台-信息提示", description = "分页返回当前账号最近信息提示，固定每页 5 条。")
    @GetMapping("/dashboard/messages")
    public ApiResponse<PageResponse<UserMessageResponse>> dashboardMessages(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "5") Integer pageSize) {
        return ApiResponse.success(authService.listMyMessages(pageNum, pageSize));
    }

}
