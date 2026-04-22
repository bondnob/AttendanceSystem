package com.attendance.admin.controller;

import com.attendance.admin.dto.CreateOrgUnitRequest;
import com.attendance.admin.dto.CreateUserRequest;
import com.attendance.admin.dto.OrgUnitResponse;
import com.attendance.admin.dto.ResetPasswordRequest;
import com.attendance.admin.dto.SendUserMessageRequest;
import com.attendance.admin.dto.SaveLeaveSignRequirementRequest;
import com.attendance.admin.dto.SaveApprovalPermissionRequest;
import com.attendance.admin.dto.UpdateEnabledRequest;
import com.attendance.admin.dto.UpdateOrgUnitRequest;
import com.attendance.admin.dto.UpdateUserRequest;
import com.attendance.admin.dto.UserSummaryResponse;
import com.attendance.admin.model.ApprovalPermission;
import com.attendance.admin.model.LeaveSignRequirement;
import com.attendance.admin.service.AdminService;
import com.attendance.common.ApiResponse;
import com.attendance.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@Tag(name = "系统管理接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "新增组织")
    @PostMapping("/org-units")
    public ApiResponse<OrgUnitResponse> createOrgUnit(@Valid @RequestBody CreateOrgUnitRequest request) {
        return ApiResponse.success("组织创建成功", adminService.createOrgUnit(request));
    }

    @Operation(summary = "组织列表")
    @GetMapping("/org-units")
    public ApiResponse<PageResponse<OrgUnitResponse>> listOrgUnits(
            @RequestParam(required = false) String orgName,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(adminService.listOrgUnits(orgName, pageNum, pageSize));
    }

    @Operation(summary = "编辑组织")
    @PutMapping("/org-units/{orgUnitId}")
    public ApiResponse<OrgUnitResponse> updateOrgUnit(@PathVariable Long orgUnitId,
                                                      @Valid @RequestBody UpdateOrgUnitRequest request) {
        return ApiResponse.success("组织更新成功", adminService.updateOrgUnit(orgUnitId, request));
    }

    @Operation(summary = "启停用组织")
    @PatchMapping("/org-units/{orgUnitId}/enabled")
    public ApiResponse<Void> updateOrgUnitEnabled(@PathVariable Long orgUnitId,
                                                  @Valid @RequestBody UpdateEnabledRequest request) {
        adminService.updateOrgUnitEnabled(orgUnitId, request);
        return ApiResponse.success("组织状态更新成功", null);
    }

    @Operation(summary = "新增用户")
    @PostMapping("/users")
    public ApiResponse<UserSummaryResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success("用户创建成功", adminService.createUser(request));
    }

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public ApiResponse<PageResponse<UserSummaryResponse>> listUsers(
            @RequestParam(required = false) String empName,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(adminService.listUsers(empName, pageNum, pageSize));
    }

    @Operation(summary = "编辑用户")
    @PutMapping("/users/{userId}")
    public ApiResponse<UserSummaryResponse> updateUser(@PathVariable Long userId,
                                                       @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("用户更新成功", adminService.updateUser(userId, request));
    }

    @Operation(summary = "启停用用户")
    @PatchMapping("/users/{userId}/enabled")
    public ApiResponse<Void> updateUserEnabled(@PathVariable Long userId,
                                               @Valid @RequestBody UpdateEnabledRequest request) {
        adminService.updateUserEnabled(userId, request);
        return ApiResponse.success("用户状态更新成功", null);
    }

    @Operation(summary = "重置密码")
    @PostMapping("/users/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long userId, @Valid @RequestBody ResetPasswordRequest request) {
        adminService.resetPassword(userId, request);
        return ApiResponse.success("密码重置成功", null);
    }

    @Operation(summary = "保存审批权限")
    @PostMapping("/approval-permissions")
    public ApiResponse<Void> saveApprovalPermission(@Valid @RequestBody SaveApprovalPermissionRequest request) {
        adminService.saveApprovalPermission(request);
        return ApiResponse.success("审批权限保存成功", null);
    }

    @Operation(summary = "审批权限列表")
    @GetMapping("/approval-permissions")
    public ApiResponse<PageResponse<ApprovalPermission>> listApprovalPermissions(
            @RequestParam(required = false) Long orgUnitId,
            @RequestParam(required = false) String leaveScope,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(adminService.listApprovalPermissions(orgUnitId, leaveScope, pageNum, pageSize));
    }

    @Operation(summary = "启停用审批权限")
    @PatchMapping("/approval-permissions/{permissionId}/enabled")
    public ApiResponse<Void> updateApprovalPermissionEnabled(@PathVariable Long permissionId,
                                                             @Valid @RequestBody UpdateEnabledRequest request) {
        adminService.updateApprovalPermissionEnabled(permissionId, request);
        return ApiResponse.success("审批权限状态更新成功", null);
    }


    @Operation(summary = "发送信息提示", description = "超级管理员向指定账号发送信息提示。")
    @PostMapping("/messages")
    public ApiResponse<Void> sendUserMessage(@Valid @RequestBody SendUserMessageRequest request) {
        adminService.sendUserMessage(request);
        return ApiResponse.success("信息提示发送成功", null);
    }
}
