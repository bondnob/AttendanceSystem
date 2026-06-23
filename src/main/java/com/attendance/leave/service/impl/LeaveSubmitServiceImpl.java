package com.attendance.leave.service.impl;

import com.attendance.exception.BizException;
import com.attendance.leave.dto.CancelLeaveRequestDto;
import com.attendance.leave.dto.CreateLeaveRequestDto;
import com.attendance.leave.dto.LeaveDetailResponse;
import com.attendance.leave.enums.LeaveRequestStatus;
import com.attendance.leave.enums.RoleCode;
import com.attendance.leave.model.ApprovalRule;
import com.attendance.leave.model.ApprovalRuleStep;
import com.attendance.leave.model.LeaveApproval;
import com.attendance.leave.model.LeaveRequest;
import com.attendance.leave.model.LeaveType;
import com.attendance.leave.model.UserAccount;
import com.attendance.leave.service.LeaveQueryService;
import com.attendance.leave.service.LeaveServiceHelper;
import com.attendance.leave.service.LeaveSubmitService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveSubmitServiceImpl implements LeaveSubmitService {

    private final LeaveServiceHelper h;
    private final LeaveQueryService leaveQueryService;

    @Override
    @Transactional
    public LeaveDetailResponse createLeave(CreateLeaveRequestDto dto) {
        UserAccount operator = h.requireCurrentUser();
        UserAccount applicant = h.requireUser(dto.getApplicantId());
        LeaveType leaveType = h.requireLeaveType(dto.getLeaveTypeId());

        if (!RoleCode.ATTENDANCE_ADMIN.equals(operator.getRoleCode())) {
            throw new BizException("只有考勤管理员可以提交请假记录单");
        }

        String applicantType = h.normalizeApplicantType(dto.getApplicantType());
        BigDecimal allowedDays = leaveType.getDefaultDays() == null ? null : BigDecimal.valueOf(leaveType.getDefaultDays());
        int daysInMonth = dto.getStartTime().toLocalDate().lengthOfMonth();
        boolean exceedsOneMonth = dto.getLeaveDays() != null && dto.getLeaveDays().compareTo(BigDecimal.valueOf(daysInMonth)) > 0;
        ApprovalRule rule = h.resolveApprovalRule(applicantType, applicant.getPositionLevelCode(), leaveType, dto.getLeaveDays(), exceedsOneMonth);
        String applicantNameSnapshot = resolveApplicantNameSnapshot(applicantType, applicant, dto);
        h.validateLeaveRequestRules(applicantNameSnapshot, leaveType, dto);
        List<ApprovalRuleStep> steps = h.prepareCreationSteps(rule.getId(), operator, applicantType, leaveType);
        if (Boolean.TRUE.equals(dto.getPartySecretaryFirst())) {
            h.swapPartySecretaryBeforeStationmaster(steps);
        }

        LeaveRequest request = new LeaveRequest();
        request.setRequestNo(h.generateRequestNo());
        request.setApplicantId(applicant.getId());
        request.setOrgUnitId(applicant.getOrgUnitId());
        request.setLeaveTypeId(leaveType.getId());
        request.setApprovalRuleId(rule.getId());
        request.setApplicantNameSnapshot(applicantNameSnapshot);
        request.setApplicantType(applicantType);
        request.setPositionLevelCode(h.resolvePositionLevel(applicantType, applicant.getPositionLevelCode()));
        request.setJobTitleSnapshot(dto.getJobTitleSnapshot().trim());
        request.setTeamLeaderSnapshot(dto.getTeamLeaderSnapshot().trim());
        request.setStartTime(dto.getStartTime());
        request.setEndTime(dto.getEndTime());
        request.setStartDate(dto.getStartTime().toLocalDate());
        request.setEndDate(dto.getEndTime().toLocalDate());
        request.setLeaveDays(dto.getLeaveDays());
        request.setAllowedDays(allowedDays);
        request.setExceedsOneMonth(exceedsOneMonth ? 1 : 0);
        request.setReason(dto.getReason());
        request.setRemark(dto.getRemark());
        request.setStatus(LeaveRequestStatus.PENDING);
        request.setCurrentStep(steps.get(0).getStepNo());
        request.setCurrentActionType(steps.get(0).getActionType());
        request.setSubmittedBy(operator.getId());
        request.setSubmittedAt(dto.getSubmittedAt());
        request.setCreatedBy(operator.getId());
        request.setPartySecretaryFirst(Boolean.TRUE.equals(dto.getPartySecretaryFirst()) ? 1 : 0);

        h.leaveRequestMapper.insert(request);

        String firstApproverRoleCode = null;
        List<LeaveApproval> newApprovals = new ArrayList<>();
        boolean hrInitiated = h.isHrOrgUnit(operator.getOrgUnitId());
        for (ApprovalRuleStep step : steps) {
            UserAccount approver = h.resolveInitialApprover(applicant.getOrgUnitId(), step);
            Long approverUserId = approver == null ? null : approver.getId();
            LeaveApproval approval = h.buildApproval(request.getId(), step, approverUserId);
            if (hrInitiated && LeaveServiceHelper.APPROVER_SOURCE_APPLICANT_ORG.equals(step.getApproverSource())
                    && RoleCode.ORG_PRINCIPAL.equals(step.getApproverRoleCode())) {
                approval.setApproverRoleCode(RoleCode.HR_SECTION_CHIEF);
            }
            h.leaveApprovalMapper.insert(approval);
            newApprovals.add(approval);
            if (firstApproverRoleCode == null) {
                firstApproverRoleCode = approval.getApproverRoleCode();
            }
        }
        h.swapApprovalStepNoIfNeeded(request, newApprovals);
        request.setCurrentStep(newApprovals.get(0).getStepNo());
        request.setCurrentApproverId(firstApproverRoleCode);
        h.leaveRequestMapper.updateApprovalState(request);

        return leaveQueryService.getLeaveDetail(request.getId());
    }

    @Override
    @Transactional
    public LeaveDetailResponse updateRejectedLeave(Long leaveId, CreateLeaveRequestDto dto) {
        UserAccount operator = h.requireCurrentUser();
        LeaveRequest request = h.requireLeaveRequest(leaveId);
        h.ensureEditableRejectedByAdmin(operator, request);

        UserAccount applicant = h.requireUser(dto.getApplicantId());
        LeaveType leaveType = h.requireLeaveType(dto.getLeaveTypeId());

        String applicantType = h.normalizeApplicantType(dto.getApplicantType());
        BigDecimal allowedDays = leaveType.getDefaultDays() == null ? null : BigDecimal.valueOf(leaveType.getDefaultDays());
        int daysInMonth = dto.getStartTime().toLocalDate().lengthOfMonth();
        boolean exceedsOneMonth = dto.getLeaveDays() != null && dto.getLeaveDays().compareTo(BigDecimal.valueOf(daysInMonth)) > 0;
        ApprovalRule rule = h.resolveApprovalRule(applicantType, applicant.getPositionLevelCode(), leaveType, dto.getLeaveDays(), exceedsOneMonth);
        String applicantNameSnapshot = resolveApplicantNameSnapshot(applicantType, applicant, dto);
        h.validateLeaveRequestRules(applicantNameSnapshot, leaveType, dto);
        List<ApprovalRuleStep> steps = h.prepareCreationSteps(rule.getId(), operator, applicantType, leaveType);
        if (Boolean.TRUE.equals(dto.getPartySecretaryFirst())) {
            h.swapPartySecretaryBeforeStationmaster(steps);
        }

        request.setRequestNo(h.generateRequestNo());
        request.setApplicantId(applicant.getId());
        request.setOrgUnitId(applicant.getOrgUnitId());
        request.setLeaveTypeId(leaveType.getId());
        request.setApprovalRuleId(rule.getId());
        request.setApplicantNameSnapshot(applicantNameSnapshot);
        request.setApplicantType(applicantType);
        request.setPositionLevelCode(h.resolvePositionLevel(applicantType, applicant.getPositionLevelCode()));
        request.setJobTitleSnapshot(dto.getJobTitleSnapshot().trim());
        request.setTeamLeaderSnapshot(dto.getTeamLeaderSnapshot().trim());
        request.setStartTime(dto.getStartTime());
        request.setEndTime(dto.getEndTime());
        request.setStartDate(dto.getStartTime().toLocalDate());
        request.setEndDate(dto.getEndTime().toLocalDate());
        request.setLeaveDays(dto.getLeaveDays());
        request.setAllowedDays(allowedDays);
        request.setExceedsOneMonth(exceedsOneMonth ? 1 : 0);
        request.setReason(dto.getReason());
        request.setRemark(dto.getRemark());
        request.setStatus(LeaveRequestStatus.PENDING);
        request.setCurrentStep(steps.get(0).getStepNo());
        request.setCurrentActionType(steps.get(0).getActionType());
        request.setSubmittedAt(dto.getSubmittedAt());
        request.setFinalApprovedAt(null);
        request.setPartySecretaryFirst(Boolean.TRUE.equals(dto.getPartySecretaryFirst()) ? 1 : 0);

        h.leaveRequestMapper.updateEditableRejected(request);

        h.leaveApprovalMapper.deleteByLeaveRequestId(leaveId);
        String firstApproverRoleCode = null;
        List<LeaveApproval> newApprovals = new ArrayList<>();
        boolean hrInitiated = h.isHrOrgUnit(operator.getOrgUnitId());
        for (ApprovalRuleStep step : steps) {
            UserAccount approver = h.resolveInitialApprover(applicant.getOrgUnitId(), step);
            Long approverUserId = approver == null ? null : approver.getId();
            LeaveApproval approval = h.buildApproval(request.getId(), step, approverUserId);
            if (hrInitiated && LeaveServiceHelper.APPROVER_SOURCE_APPLICANT_ORG.equals(step.getApproverSource())
                    && RoleCode.ORG_PRINCIPAL.equals(step.getApproverRoleCode())) {
                approval.setApproverRoleCode(RoleCode.HR_SECTION_CHIEF);
            }
            h.leaveApprovalMapper.insert(approval);
            newApprovals.add(approval);
            if (firstApproverRoleCode == null) {
                firstApproverRoleCode = approval.getApproverRoleCode();
            }
        }
        h.swapApprovalStepNoIfNeeded(request, newApprovals);
        request.setCurrentStep(newApprovals.get(0).getStepNo());
        request.setCurrentApproverId(firstApproverRoleCode);
        h.leaveRequestMapper.updateApprovalState(request);

        return leaveQueryService.getLeaveDetail(leaveId);
    }

    @Override
    @Transactional
    public void deleteRejectedLeave(Long leaveId) {
        UserAccount operator = h.requireCurrentUser();
        LeaveRequest request = h.requireLeaveRequest(leaveId);
        h.ensureEditableRejectedByAdmin(operator, request);
        h.leaveApprovalMapper.deleteByLeaveRequestId(leaveId);
        h.leaveRequestMapper.deleteById(leaveId);
    }

    @Override
    @Transactional
    public LeaveDetailResponse cancelLeave(Long leaveId, CancelLeaveRequestDto dto) {
        UserAccount operator = h.requireCurrentUser();
        LeaveRequest request = h.requireLeaveRequest(leaveId);
        if (!RoleCode.ATTENDANCE_ADMIN.equals(operator.getRoleCode())) {
            throw new BizException("只有考勤管理员可以撤销请假单");
        }
        if (!operator.getId().equals(request.getSubmittedBy())) {
            throw new BizException("只能撤销本人发起的请假单");
        }
        if (!operator.getOrgUnitId().equals(request.getOrgUnitId())) {
            throw new BizException("只能撤销本单位请假单");
        }
        if (LeaveRequestStatus.APPROVED.equals(request.getStatus())) {
            throw new BizException("已通过的请假单不能撤销");
        }
        if (LeaveRequestStatus.REJECTED.equals(request.getStatus())) {
            throw new BizException("已驳回的请假单不能撤销");
        }
        if (LeaveRequestStatus.CANCELLED.equals(request.getStatus())) {
            throw new BizException("请假单已撤销，请勿重复操作");
        }
        if (!LeaveRequestStatus.PENDING.equals(request.getStatus())
                && !LeaveRequestStatus.APPROVING.equals(request.getStatus())) {
            throw new BizException("当前状态不允许撤销");
        }

        String cancelComment = h.buildCancelComment(dto);
        request.setStatus(LeaveRequestStatus.CANCELLED);
        request.setCurrentActionType(null);
        request.setCurrentApproverId(null);
        h.leaveRequestMapper.updateApprovalState(request);
        h.leaveApprovalMapper.cancelPendingByLeaveRequestId(leaveId, cancelComment);
        return leaveQueryService.getLeaveDetail(leaveId);
    }

    @Override
    @Transactional
    public LeaveDetailResponse updateSubmittedAt(Long leaveId, LocalDateTime submittedAt) {
        UserAccount operator = h.requireCurrentUser();
        if (!"SYSTEM_ADMIN".equals(operator.getRoleCode())) {
            throw new BizException("只有超级管理员可以修改申请时间");
        }
        LeaveRequest request = h.requireLeaveRequest(leaveId);
        h.leaveRequestMapper.updateSubmittedAt(leaveId, submittedAt);
        request.setSubmittedAt(submittedAt);
        return leaveQueryService.getLeaveDetail(leaveId);
    }

    @Override
    @Transactional
    public LeaveDetailResponse updateSignatureDate(Long leaveId, Integer stepNo, LocalDate signatureDate) {
        UserAccount operator = h.requireCurrentUser();
        if (!"SYSTEM_ADMIN".equals(operator.getRoleCode())) {
            throw new BizException("只有超级管理员可以修改签字日期");
        }
        h.requireLeaveRequest(leaveId);
        int rows = h.leaveApprovalMapper.updateSignatureDate(leaveId, stepNo, signatureDate);
        if (rows == 0) {
            throw new BizException("未找到对应的审批节点");
        }
        return leaveQueryService.getLeaveDetail(leaveId);
    }

    // ==================== 私有方法 ====================

    private String resolveApplicantNameSnapshot(String applicantType, UserAccount applicant, CreateLeaveRequestDto dto) {
        String inputName = dto.getApplicantName() == null ? null : dto.getApplicantName().trim();
        if ((LeaveServiceHelper.APPLICANT_TYPE_EMPLOYEE.equals(applicantType)
                || LeaveServiceHelper.APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType)
                || LeaveServiceHelper.APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(applicantType)
                || LeaveServiceHelper.APPLICANT_TYPE_WORKSHOP_DIRECTOR.equals(applicantType))
                && inputName != null && !inputName.isBlank()) {
            return inputName;
        }
        return applicant.getEmpName();
    }
}
