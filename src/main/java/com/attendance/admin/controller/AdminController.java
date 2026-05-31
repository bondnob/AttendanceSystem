package com.attendance.admin.controller;

import com.attendance.admin.dto.CreateOrgUnitRequest;
import com.attendance.admin.dto.CreateTeamNameRequest;
import com.attendance.admin.dto.CreateUserRequest;
import com.attendance.admin.dto.OrgUnitResponse;
import com.attendance.admin.dto.ResetPasswordRequest;
import com.attendance.admin.dto.SendUserMessageRequest;
import com.attendance.admin.dto.SaveLeaveSignRequirementRequest;
import com.attendance.admin.dto.SaveApprovalPermissionRequest;
import com.attendance.admin.dto.TeamNameResponse;
import com.attendance.admin.dto.UpdateEnabledRequest;
import com.attendance.admin.dto.UpdateOrgUnitRequest;
import com.attendance.admin.dto.UpdateTeamNameRequest;
import com.attendance.admin.dto.UpdateUserSignatureRequest;
import com.attendance.admin.dto.UpdateUserRequest;
import com.attendance.admin.dto.UserSummaryResponse;
import com.attendance.admin.model.ApprovalPermission;
import com.attendance.admin.model.LeaveSignRequirement;
import com.attendance.admin.service.AdminService;
import com.attendance.common.ApiResponse;
import com.attendance.common.PageResponse;
import com.attendance.leave.dto.LeaveListItemResponse;
import com.attendance.leave.service.LeaveService;
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
    private final LeaveService leaveService;

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

    @Operation(summary = "上传或更改审批人电子签名", description = "超级管理员为可审批账号上传签名文件，或直接保存已有签名地址。")
    @PatchMapping("/users/{userId}/signature")
    public ApiResponse<UserSummaryResponse> updateUserSignature(@PathVariable Long userId,
                                                                @ModelAttribute UpdateUserSignatureRequest request) {
        return ApiResponse.success("电子签名更新成功", adminService.updateUserSignature(userId, request));
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

    @Operation(summary = "按部门获取班组名称列表", description = "获取指定部门下所有启用的班组名称，用于下拉选择。")
    @GetMapping("/team-names")
    public ApiResponse<List<TeamNameResponse>> listTeamNamesByOrgUnit(@RequestParam Long orgUnitId) {
        return ApiResponse.success(adminService.listTeamNamesByOrgUnit(orgUnitId));
    }

    @Operation(summary = "班组名称分页列表", description = "分页查询班组名称，支持按部门和名称筛选。不传分页参数则返回全部。")
    @GetMapping("/team-names/page")
    public ApiResponse<PageResponse<TeamNameResponse>> listTeamNames(
            @RequestParam(required = false) Long orgUnitId,
            @RequestParam(required = false) String teamName,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(adminService.listTeamNames(orgUnitId, teamName, pageNum, pageSize));
    }

    @Operation(summary = "新增班组名称")
    @PostMapping("/team-names")
    public ApiResponse<TeamNameResponse> createTeamName(@Valid @RequestBody CreateTeamNameRequest request) {
        return ApiResponse.success("班组名称创建成功", adminService.createTeamName(request));
    }

    @Operation(summary = "编辑班组名称")
    @PutMapping("/team-names/{id}")
    public ApiResponse<TeamNameResponse> updateTeamName(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateTeamNameRequest request) {
        return ApiResponse.success("班组名称更新成功", adminService.updateTeamName(id, request));
    }

    @Operation(summary = "删除班组名称")
    @DeleteMapping("/team-names/{id}")
    public ApiResponse<Void> deleteTeamName(@PathVariable Long id) {
        adminService.deleteTeamName(id);
        return ApiResponse.success("班组名称删除成功", null);
    }

    @Operation(summary = "所有请假记录", description = "超级管理员查看所有请假记录列表，支持按状态、假别和申请人姓名筛选。")
    @GetMapping("/leaves")
    public ApiResponse<PageResponse<LeaveListItemResponse>> listAllLeaves(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) String applicantName,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(leaveService.listAllLeaves(status, leaveTypeId, applicantName, pageNum, pageSize));
    }
}
