package com.attendance.leave.controller;

import com.attendance.common.ApiResponse;
import com.attendance.common.PageResponse;
import com.attendance.leave.dto.ApproveLeaveWithSignatureDto;
import com.attendance.leave.dto.ApprovalSignatureUploadResponse;
import com.attendance.leave.dto.BatchLeavePdfRequest;
import com.attendance.leave.dto.BatchLeavePdfResponse;
import com.attendance.leave.dto.CancelLeaveRequestDto;
import com.attendance.leave.dto.BatchApproveLeaveDto;
import com.attendance.leave.dto.BatchApproveLeaveResponse;
import com.attendance.leave.dto.CreateLeaveRequestDto;
import com.attendance.leave.dto.HandwrittenSignatureDto;
import com.attendance.leave.dto.LeaveDetailResponse;
import com.attendance.leave.dto.UpdateLeaveSubmittedTimeDto;
import com.attendance.leave.dto.LeaveListItemResponse;
import com.attendance.leave.dto.LeavePdfResponse;
import com.attendance.leave.dto.LeaveStatusOptionResponse;
import com.attendance.leave.dto.PendingSummaryResponse;
import com.attendance.leave.dto.SelectedApproverResponse;
import com.attendance.leave.dto.SelectApproversDto;
import com.attendance.leave.dto.UploadApprovalSignatureDto;
import com.attendance.leave.model.LeaveType;
import com.attendance.leave.service.LeaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@Tag(name = "请假接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    @Operation(summary = "提交请假记录单", description = "考勤管理员提交本单位职工的请假记录单，系统根据人员级别、假别和是否超时自动匹配审批流程。")
    @PostMapping
    public ApiResponse<LeaveDetailResponse> createLeave(@Valid @RequestBody CreateLeaveRequestDto request) {
        return ApiResponse.success("请假记录单提交成功", leaveService.createLeave(request));
    }

    @Operation(summary = "修改驳回请假单", description = "考勤管理员可以修改本人发起且已驳回的请假单，系统会重新提交并重建审批流。")
    @PutMapping("/{leaveId}")
    public ApiResponse<LeaveDetailResponse> updateRejectedLeave(@PathVariable Long leaveId,
                                                                @Valid @RequestBody CreateLeaveRequestDto request) {
        return ApiResponse.success("请假记录单修改并重新提交成功", leaveService.updateRejectedLeave(leaveId, request));
    }

    @Operation(summary = "删除驳回请假单", description = "考勤管理员可以删除本人发起且已驳回的请假单。")
    @DeleteMapping("/{leaveId}")
    public ApiResponse<Void> deleteRejectedLeave(@PathVariable Long leaveId) {
        leaveService.deleteRejectedLeave(leaveId);
        return ApiResponse.success("驳回请假单删除成功", null);
    }

    @Operation(summary = "审批请假单", description = "处理审批节点，审批签名仅使用超级管理员预先为当前账号配置的电子签名。")
    @PostMapping("/{leaveId}/approve")
    public ApiResponse<LeaveDetailResponse> approve(@PathVariable Long leaveId,
                                                    @Valid @ModelAttribute ApproveLeaveWithSignatureDto request) {
        return ApiResponse.success("审批完成", leaveService.approve(leaveId, request));
    }

    @Operation(summary = "批量审批请假单", description = "对当前账号能够处理的多张请假单进行一键审批，审批签名仅使用超级管理员预先配置的电子签名。")
    @PostMapping("/batch-approve")
    public ApiResponse<BatchApproveLeaveResponse> batchApprove(@Valid @ModelAttribute BatchApproveLeaveDto request) {
        return ApiResponse.success("批量审批完成", leaveService.batchApprove(request));
    }

    @Operation(summary = "撤销请假单", description = "考勤管理员撤销自己发起的请假单，撤销后状态变为已取消。")
    @PostMapping("/{leaveId}/cancel")
    public ApiResponse<LeaveDetailResponse> cancel(@PathVariable Long leaveId,
                                                   @RequestBody(required = false) CancelLeaveRequestDto request) {
        return ApiResponse.success("撤销成功", leaveService.cancelLeave(leaveId, request));
    }

    @Operation(summary = "上传手写签名", description = "请假人或班组长上传鼠标手写签名/日期图片，type 取值 APPLICANT、APPLICANT_DATE 或 TEAM_LEADER。")
    @PostMapping("/{leaveId}/handwritten-signature")
    public ApiResponse<LeaveDetailResponse> uploadHandwrittenSignature(@PathVariable Long leaveId,
                                                                        @Valid @ModelAttribute HandwrittenSignatureDto request) {
        return ApiResponse.success("手写签名上传成功", leaveService.uploadHandwrittenSignature(leaveId, request));
    }

    @Operation(summary = "修改请假单申请时间", description = "超级管理员修改指定请假单的申请时间。")
    @PutMapping("/{leaveId}/submitted-at")
    public ApiResponse<LeaveDetailResponse> updateSubmittedAt(@PathVariable Long leaveId,
                                                               @Valid @RequestBody UpdateLeaveSubmittedTimeDto request) {
        return ApiResponse.success("申请时间修改成功", leaveService.updateSubmittedAt(leaveId, request.getSubmittedAt()));
    }

    @Operation(summary = "获取审批电子签名", description = "返回当前账号已由超级管理员预先配置的电子签名地址；未配置则不允许审批。")
    @PostMapping("/{leaveId}/approval-signature")
    public ApiResponse<ApprovalSignatureUploadResponse> uploadApprovalSignature(@PathVariable Long leaveId,
                                                                                @Valid @ModelAttribute UploadApprovalSignatureDto request) {
        return ApiResponse.success("电子签名上传成功", leaveService.uploadApprovalSignature(leaveId, request));
    }

    @Operation(summary = "选择后续领导", description = "科室车间负责人在返回节点选择后续领导审批人。")
    @PostMapping("/{leaveId}/select-approvers")
    public ApiResponse<LeaveDetailResponse> selectApprovers(@PathVariable Long leaveId,
                                                            @Valid @RequestBody SelectApproversDto request) {
        return ApiResponse.success("选择审批人完成", leaveService.selectApprovers(leaveId, request));
    }

    @Operation(summary = "重选后续领导", description = "科室车间负责人在所选领导尚未审批时，可撤销之前的选择并重新选择领导审批人。")
    @PostMapping("/{leaveId}/reselect-approvers")
    public ApiResponse<LeaveDetailResponse> reSelectApprovers(@PathVariable Long leaveId,
                                                              @Valid @RequestBody SelectApproversDto request) {
        return ApiResponse.success("重选领导成功", leaveService.reSelectApprovers(leaveId, request));
    }

    @Operation(summary = "请假单详情", description = "查询单个请假单详情和完整审批记录。")
    @GetMapping("/{leaveId}")
    public ApiResponse<LeaveDetailResponse> getDetail(@PathVariable Long leaveId) {
        return ApiResponse.success(leaveService.getLeaveDetail(leaveId));
    }

    @Operation(summary = "请假单 PDF", description = "返回已生成的请假单 PDF 地址，供预览和打印。")
    @GetMapping("/{leaveId}/pdf")
    public ApiResponse<LeavePdfResponse> getPdf(@PathVariable Long leaveId) {
        return ApiResponse.success(leaveService.getLeavePdf(leaveId));
    }

    @Operation(summary = "批量下载请假单 PDF", description = "考勤管理员可按请假时间段，批量生成本单位已审批完成的请假记录单合并 PDF。")
    @PostMapping("/pdf/batch")
    public ApiResponse<BatchLeavePdfResponse> batchDownloadPdf(@Valid @RequestBody BatchLeavePdfRequest request) {
        return ApiResponse.success(leaveService.batchDownloadPdf(request));
    }

    @Operation(summary = "可选择领导", description = "根据请假规则返回当前请假单在选择领导节点可选的领导列表。")
    @GetMapping("/{leaveId}/selected-approvers")
    public ApiResponse<List<SelectedApproverResponse>> getSelectedApprovers(@PathVariable Long leaveId) {
        return ApiResponse.success(leaveService.getSelectedApprovers(leaveId));
    }

    @Operation(summary = "请假单列表", description = "根据当前登录人的数据权限查询请假单列表，可按状态过滤。")
    @GetMapping
    public ApiResponse<PageResponse<LeaveListItemResponse>> list(@RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) Long leaveTypeId,
                                                                 @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                                 @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(leaveService.listLeaves(status, leaveTypeId, pageNum, pageSize));
    }

    @Operation(summary = "近三个月请假单列表", description = "根据当前登录人的数据权限返回近三个月请假单列表，仅包含请假开始时间属于本月、上个月、上上个月的数据。")
    @GetMapping("/approval-list/recent-three-months")
    public ApiResponse<PageResponse<LeaveListItemResponse>> listRecentThreeMonthApprovalLeaves(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(leaveService.listRecentThreeMonthApprovalLeaves(status, leaveTypeId, pageNum, pageSize));
    }

    @Operation(summary = "请假类型列表", description = "返回所有请假类型，供前端下拉选择。")
    @GetMapping("/types")
    public ApiResponse<List<LeaveType>> listLeaveTypes() {
        return ApiResponse.success(leaveService.listLeaveTypes());
    }

    @Operation(summary = "请假状态列表", description = "返回所有请假状态，供前端筛选。")
    @GetMapping("/statuses")
    public ApiResponse<List<LeaveStatusOptionResponse>> listLeaveStatuses() {
        return ApiResponse.success(leaveService.listLeaveStatuses());
    }

    @Operation(summary = "待审批数量", description = "返回当前账号的待处理数量。")
    @GetMapping("/pending-summary")
    public ApiResponse<PendingSummaryResponse> pendingSummary() {
        return ApiResponse.success(leaveService.getPendingSummary());
    }

}
