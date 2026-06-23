package com.attendance.leave.service.impl;

import com.attendance.common.PageResponse;
import com.attendance.exception.BizException;
import com.attendance.leave.dto.ApprovalRecordResponse;
import com.attendance.leave.dto.BatchLeavePdfRequest;
import com.attendance.leave.dto.BatchLeavePdfResponse;
import com.attendance.leave.dto.LeaveDetailResponse;
import com.attendance.leave.dto.LeaveListItemResponse;
import com.attendance.leave.dto.LeavePdfResponse;
import com.attendance.leave.dto.LeaveStatusOptionResponse;
import com.attendance.leave.dto.PendingSummaryResponse;
import com.attendance.leave.dto.SelectedApproverResponse;
import com.attendance.leave.enums.LeaveRequestStatus;
import com.attendance.leave.enums.RoleCode;
import com.attendance.leave.model.ApprovalRuleStep;
import com.attendance.leave.model.LeaveApproval;
import com.attendance.leave.model.LeaveRequest;
import com.attendance.leave.model.LeaveType;
import com.attendance.leave.model.UserAccount;
import com.attendance.leave.service.LeaveQueryService;
import com.attendance.leave.service.LeaveServiceHelper;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LeaveQueryServiceImpl implements LeaveQueryService {

    private final LeaveServiceHelper h;

    @Override
    public LeaveDetailResponse getLeaveDetail(Long leaveId) {
        LeaveRequest request = h.requireLeaveRequest(leaveId);
        UserAccount applicant = h.requireUser(request.getApplicantId());
        LeaveType leaveType = h.requireLeaveType(request.getLeaveTypeId());
        List<ApprovalRecordResponse> approvals = h.leaveApprovalMapper.findByLeaveRequestId(leaveId).stream()
                .map(h::toApprovalRecordResponse)
                .collect(Collectors.toList());
        String pdfUrl = resolveOrCreatePdfUrl(request, applicant, leaveType, approvals);

        return LeaveDetailResponse.builder()
                .id(request.getId())
                .requestNo(request.getRequestNo())
                .status(request.getStatus())
                .currentStep(request.getCurrentStep())
                .currentActionType(request.getCurrentActionType())
                .approvalRuleId(request.getApprovalRuleId())
                .applicantId(applicant.getId())
                .applicantName(request.getApplicantNameSnapshot())
                .applicantType(request.getApplicantType())
                .positionLevelCode(request.getPositionLevelCode())
                .jobTitleSnapshot(request.getJobTitleSnapshot())
                .teamLeaderSnapshot(request.getTeamLeaderSnapshot())
                .orgUnitId(request.getOrgUnitId())
                .leaveTypeId(leaveType.getId())
                .leaveTypeName(leaveType.getLeaveName())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .leaveDays(request.getLeaveDays())
                .allowedDays(request.getAllowedDays())
                .exceedsOneMonth(request.getExceedsOneMonth())
                .reason(request.getReason())
                .remark(request.getRemark())
                .submittedBy(request.getSubmittedBy())
                .submittedAt(request.getSubmittedAt())
                .finalApprovedAt(request.getFinalApprovedAt())
                .applicantSignatureUrl(request.getApplicantSignatureUrl())
                .applicantDateSignatureUrl(request.getApplicantDateSignatureUrl())
                .teamLeaderSignatureUrl(request.getTeamLeaderSignatureUrl())
                .teamLeaderSignatureDate(request.getTeamLeaderSignatureDate())
                .partySecretaryFirst(request.getPartySecretaryFirst())
                .pdfUrl(pdfUrl)
                .approvals(approvals)
                .build();
    }

    @Override
    public LeavePdfResponse getLeavePdf(Long leaveId) {
        LeaveDetailResponse detail = getLeaveDetail(leaveId);
        return LeavePdfResponse.builder()
                .pdfUrl(detail.getPdfUrl())
                .build();
    }

    @Override
    public BatchLeavePdfResponse batchDownloadPdf(BatchLeavePdfRequest dto) {
        UserAccount operator = h.requireCurrentUser();
        if (!RoleCode.ATTENDANCE_ADMIN.equals(operator.getRoleCode())) {
            throw new BizException("只有考勤管理员可以批量下载请假记录单");
        }

        List<LeaveRequest> requests = loadApprovedRequestsForBatchDownload(operator, dto);
        List<Path> pdfPaths = new ArrayList<>();
        for (LeaveRequest request : requests) {
            LeaveDetailResponse detail = getLeaveDetail(request.getId());
            if (detail.getPdfUrl() == null || detail.getPdfUrl().isBlank()) {
                throw new BizException("请假单 PDF 生成失败: " + request.getRequestNo());
            }
            Path pdfPath = h.leaveDocumentService.resolveStoredFilePath(detail.getPdfUrl());
            if (pdfPath == null) {
                throw new BizException("请假单 PDF 文件不存在: " + request.getRequestNo());
            }
            pdfPaths.add(pdfPath);
        }

        String pdfUrl = h.leaveDocumentService.generateMergedPdf(pdfPaths);
        return BatchLeavePdfResponse.builder()
                .pdfUrl(pdfUrl)
                .recordCount(requests.size())
                .build();
    }

    @Override
    public List<SelectedApproverResponse> getSelectedApprovers(Long leaveId) {
        LeaveRequest request = h.requireLeaveRequest(leaveId);
        LeaveApproval pending = h.requireCurrentPendingApproval(request);
        if (!LeaveRequestStatus.PENDING.equals(request.getStatus())
                && !LeaveRequestStatus.APPROVING.equals(request.getStatus())) {
            throw new BizException("当前请假单状态不允许审批");
        }
        if (!LeaveServiceHelper.ACTION_SELECT.equals(pending.getActionType())) {
            return List.of();
        }
        ApprovalRuleStep currentRuleStep = h.requireRuleStep(request.getApprovalRuleId(), pending.getRuleStepId());
        return h.resolveSelectableApprovers(request, pending, currentRuleStep);
    }

    @Override
    public PageResponse<LeaveListItemResponse> listLeaves(String status, Long leaveTypeId, Integer pageNum, Integer pageSize) {
        UserAccount operator = h.requireCurrentUser();
        String normalizedStatus = normalizeListStatus(operator, status);
        Long orgUnitId = null;
        Long applicantId = null;
        if ("ORG".equals(operator.getDataScope())) {
            orgUnitId = operator.getOrgUnitId();
        } else if (!"ALL".equals(operator.getDataScope())) {
            applicantId = operator.getId();
        }
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int offset = (safePageNum - 1) * safePageSize;
        LocalDateTime monthStart = h.currentMonthStart();
        LocalDateTime monthEnd = monthStart.plusMonths(1);

        Long total;
        List<LeaveListItemResponse> records;
        if (shouldUsePendingApproverView(operator)) {
            total = h.leaveRequestMapper.countByResponsibleApprover(
                    operator.getId(), operator.getRoleCode(), operator.getOrgUnitId(), normalizedStatus, leaveTypeId, monthStart, monthEnd);
            List<LeaveRequest> requests = h.leaveRequestMapper.findPageByResponsibleApprover(
                    operator.getId(), operator.getRoleCode(), operator.getOrgUnitId(), normalizedStatus, leaveTypeId,
                    monthStart, monthEnd, offset, safePageSize);
            records = h.toLeaveListItemResponses(requests);
        } else {
            total = h.leaveRequestMapper.countByScope(orgUnitId, applicantId, normalizedStatus, leaveTypeId, monthStart, monthEnd);
            List<LeaveRequest> requests = h.leaveRequestMapper.findPageByScope(
                    orgUnitId, applicantId, normalizedStatus, leaveTypeId, monthStart, monthEnd, offset, safePageSize);
            records = h.toLeaveListItemResponses(requests);
        }
        return PageResponse.<LeaveListItemResponse>builder()
                .total(total == null ? 0L : total)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .records(records)
                .build();
    }

    @Override
    public PageResponse<LeaveListItemResponse> listRecentThreeMonthApprovalLeaves(String status, Long leaveTypeId, Integer pageNum, Integer pageSize) {
        UserAccount operator = h.requireCurrentUser();
        String normalizedStatus = normalizeListStatus(operator, status);
        Long orgUnitId = null;
        Long applicantId = null;
        if ("ORG".equals(operator.getDataScope())) {
            orgUnitId = operator.getOrgUnitId();
        } else if (!"ALL".equals(operator.getDataScope())) {
            applicantId = operator.getId();
        }
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int offset = (safePageNum - 1) * safePageSize;
        LocalDateTime monthEnd = h.currentMonthStart();
        LocalDateTime monthStart = h.currentMonthStart().minusMonths(3);

        Long total = h.leaveRequestMapper.countByScope(orgUnitId, applicantId, normalizedStatus, leaveTypeId, monthStart, monthEnd);
        List<LeaveRequest> requests = h.leaveRequestMapper.findPageByScope(orgUnitId, applicantId, normalizedStatus, leaveTypeId, monthStart, monthEnd, offset, safePageSize);
        List<LeaveListItemResponse> records = h.toLeaveListItemResponses(requests);
        return PageResponse.<LeaveListItemResponse>builder()
                .total(total == null ? 0L : total)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .records(records)
                .build();
    }

    @Override
    public PageResponse<LeaveListItemResponse> listAllLeaves(String status, Long leaveTypeId, String applicantName, Integer pageNum, Integer pageSize) {
        UserAccount operator = h.requireCurrentUser();
        if (!"SYSTEM_ADMIN".equals(operator.getRoleCode())) {
            throw new BizException("只有超级管理员可以查看所有请假记录");
        }
        String keyword = applicantName == null ? null : applicantName.trim();
        if (keyword != null && keyword.isEmpty()) {
            keyword = null;
        }
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int offset = (safePageNum - 1) * safePageSize;
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        Long total = h.leaveRequestMapper.countAll(status, leaveTypeId, keyword, threeMonthsAgo);
        List<LeaveRequest> requests = h.leaveRequestMapper.findAllPage(status, leaveTypeId, keyword, threeMonthsAgo, offset, safePageSize);
        List<LeaveListItemResponse> records = h.toLeaveListItemResponses(requests);
        return PageResponse.<LeaveListItemResponse>builder()
                .total(total == null ? 0L : total)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .records(records)
                .build();
    }

    @Override
    public List<LeaveType> listLeaveTypes() {
        return h.leaveTypeMapper.findAll();
    }

    @Override
    public List<LeaveStatusOptionResponse> listLeaveStatuses() {
        UserAccount operator = h.requireCurrentUser();
        if (RoleCode.ATTENDANCE_ADMIN.equals(operator.getRoleCode())) {
            return List.of(
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.PENDING).name("待审批").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.APPROVING).name("审批中").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.APPROVED).name("已通过").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.REJECTED).name("已驳回").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.CANCELLED).name("已撤销").build()
            );
        }
        if (RoleCode.ORG_PRINCIPAL.equals(operator.getRoleCode())
                || RoleCode.WORKSHOP_PARTY_SECRETARY.equals(operator.getRoleCode())) {
            return List.of(
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.PENDING).name("待审批").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.APPROVING).name("审批中").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.APPROVED).name("已通过").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.REJECTED).name("已驳回").build()
            );
        }
        return List.of(
                LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.PENDING).name("待审批").build(),
                LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.APPROVING).name("审批中").build(),
                LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.APPROVED).name("已通过").build(),
                LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.REJECTED).name("已驳回").build()
        );
    }

    @Override
    public PendingSummaryResponse getPendingSummary() {
        UserAccount operator = h.requireCurrentUser();
        Long count = h.leaveApprovalMapper.countPendingForUser(operator.getId(), operator.getRoleCode(), operator.getOrgUnitId());
        return PendingSummaryResponse.builder()
                .pendingCount(count == null ? 0L : count)
                .build();
    }

    // ==================== 私有方法 ====================

    private String resolveOrCreatePdfUrl(LeaveRequest request, UserAccount applicant,
                                          LeaveType leaveType, List<ApprovalRecordResponse> approvals) {
        if (!LeaveRequestStatus.APPROVED.equals(request.getStatus()) || request.getFinalApprovedAt() == null) {
            return null;
        }
        LeaveDetailResponse detail = LeaveDetailResponse.builder()
                .id(request.getId())
                .requestNo(request.getRequestNo())
                .status(request.getStatus())
                .currentStep(request.getCurrentStep())
                .currentActionType(request.getCurrentActionType())
                .approvalRuleId(request.getApprovalRuleId())
                .applicantId(applicant.getId())
                .applicantName(request.getApplicantNameSnapshot())
                .applicantType(request.getApplicantType())
                .positionLevelCode(request.getPositionLevelCode())
                .jobTitleSnapshot(request.getJobTitleSnapshot())
                .teamLeaderSnapshot(request.getTeamLeaderSnapshot())
                .orgUnitId(request.getOrgUnitId())
                .leaveTypeId(leaveType.getId())
                .leaveTypeName(leaveType.getLeaveName())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .leaveDays(request.getLeaveDays())
                .allowedDays(request.getAllowedDays())
                .exceedsOneMonth(request.getExceedsOneMonth())
                .reason(request.getReason())
                .remark(request.getRemark())
                .submittedBy(request.getSubmittedBy())
                .submittedAt(request.getSubmittedAt())
                .finalApprovedAt(request.getFinalApprovedAt())
                .applicantSignatureUrl(request.getApplicantSignatureUrl())
                .applicantDateSignatureUrl(request.getApplicantDateSignatureUrl())
                .teamLeaderSignatureUrl(request.getTeamLeaderSignatureUrl())
                .teamLeaderSignatureDate(request.getTeamLeaderSignatureDate())
                .partySecretaryFirst(request.getPartySecretaryFirst())
                .approvals(approvals)
                .build();
        return h.leaveDocumentService.generatePdf(request.getId(), detail);
    }

    private boolean shouldUsePendingApproverView(UserAccount operator) {
        return !RoleCode.ATTENDANCE_ADMIN.equals(operator.getRoleCode())
                && !RoleCode.ORG_PRINCIPAL.equals(operator.getRoleCode())
                && !RoleCode.WORKSHOP_PARTY_SECRETARY.equals(operator.getRoleCode())
                && !"NONE".equals(operator.getApprovalScope());
    }

    private String normalizeListStatus(UserAccount operator, String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        if (RoleCode.ATTENDANCE_ADMIN.equals(operator.getRoleCode())) {
            if (LeaveRequestStatus.CANCELLED.equals(status)) {
                throw new BizException("考勤管理员不可查询已取消状态");
            }
            return status;
        }

        if (RoleCode.ORG_PRINCIPAL.equals(operator.getRoleCode())
                || RoleCode.WORKSHOP_PARTY_SECRETARY.equals(operator.getRoleCode())
                || RoleCode.HR_SECTION_CHIEF.equals(operator.getRoleCode())) {
            if (!List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVING,
                    LeaveRequestStatus.APPROVED, LeaveRequestStatus.REJECTED).contains(status)) {
                throw new BizException("当前角色仅可查询待审批、审批中、已通过、已驳回状态");
            }
            return status;
        }

        if (!List.of(LeaveRequestStatus.APPROVING, LeaveRequestStatus.APPROVED,
                LeaveRequestStatus.REJECTED).contains(status)) {
            throw new BizException("当前角色仅可查询审批中、已通过、已驳回状态");
        }
        return status;
    }

    private List<LeaveRequest> loadApprovedRequestsForBatchDownload(UserAccount operator, BatchLeavePdfRequest dto) {
        LocalDate startDate = dto == null ? null : dto.getStartDate();
        LocalDate endDate = dto == null ? null : dto.getEndDate();
        if (startDate == null || endDate == null) {
            throw new BizException("请填写请假时间段");
        }
        if (endDate.isBefore(startDate)) {
            throw new BizException("结束日期不能早于开始日期");
        }
        List<LeaveRequest> requests = h.leaveRequestMapper.findApprovedByDateRange(operator.getOrgUnitId(), startDate, endDate);
        if (requests.isEmpty()) {
            throw new BizException("该时间段内没有已审批完成的请假记录单");
        }
        return requests;
    }
}
