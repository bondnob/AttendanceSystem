package com.attendance.leave.service.impl;

import com.attendance.exception.BizException;
import com.attendance.leave.dto.ApproveLeaveWithSignatureDto;
import com.attendance.leave.dto.ApprovalSignatureUploadResponse;
import com.attendance.leave.dto.BatchApproveLeaveDto;
import com.attendance.leave.dto.BatchApproveLeaveResponse;
import com.attendance.leave.dto.HandwrittenSignatureDto;
import com.attendance.leave.dto.LeaveDetailResponse;
import com.attendance.leave.dto.SelectApproversDto;
import com.attendance.leave.dto.UploadApprovalSignatureDto;
import com.attendance.leave.enums.ApprovalStatus;
import com.attendance.leave.enums.LeaveRequestStatus;
import com.attendance.leave.enums.RoleCode;
import com.attendance.leave.model.ApprovalRuleStep;
import com.attendance.leave.model.LeaveApproval;
import com.attendance.leave.model.LeaveRequest;
import com.attendance.leave.model.UserAccount;
import com.attendance.leave.service.LeaveApprovalService;
import com.attendance.leave.service.LeaveQueryService;
import com.attendance.leave.service.LeaveServiceHelper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaveApprovalServiceImpl implements LeaveApprovalService {

    private final LeaveServiceHelper h;
    private final LeaveQueryService leaveQueryService;

    @Override
    @Transactional
    public LeaveDetailResponse approve(Long leaveId, ApproveLeaveWithSignatureDto dto) {
        UserAccount operator = h.requireCurrentUser();
        LocalDate effectiveSignatureDate = dto.getSignatureDate();
        if (effectiveSignatureDate == null && dto.getApprovedAt() != null) {
            effectiveSignatureDate = dto.getApprovedAt().toLocalDate();
        }
        return approveInternal(operator, leaveId, dto.getApproved(), dto.getComment(),
                dto.getSignatureFile(), dto.getSignatureUrl(), null, effectiveSignatureDate, dto.getApprovedAt());
    }

    @Override
    @Transactional
    public BatchApproveLeaveResponse batchApprove(BatchApproveLeaveDto dto) {
        UserAccount operator = h.requireCurrentUser();
        byte[] signatureBytes = null;
        String originalFilename = null;
        if (dto.getSignatureFile() != null && !dto.getSignatureFile().isEmpty()) {
            try {
                signatureBytes = dto.getSignatureFile().getBytes();
                originalFilename = dto.getSignatureFile().getOriginalFilename();
            } catch (IOException ex) {
                throw new BizException("电子签名上传失败");
            }
        }

        LocalDate effectiveSignatureDate = dto.getSignatureDate();
        if (effectiveSignatureDate == null && dto.getApprovedAt() != null) {
            effectiveSignatureDate = dto.getApprovedAt().toLocalDate();
        }

        List<LeaveDetailResponse> records = new ArrayList<>();
        for (Long leaveId : dto.getLeaveIds()) {
            records.add(approveInternal(operator, leaveId, dto.getApproved(), dto.getComment(),
                    null, dto.getSignatureUrl(),
                    signatureBytes == null ? null : new LeaveServiceHelper.BatchSignaturePayload(signatureBytes, originalFilename),
                    effectiveSignatureDate, dto.getApprovedAt()));
        }
        return BatchApproveLeaveResponse.builder()
                .approvedCount(records.size())
                .records(records)
                .build();
    }

    @Override
    public ApprovalSignatureUploadResponse uploadApprovalSignature(Long leaveId, UploadApprovalSignatureDto dto) {
        UserAccount operator = h.requireCurrentUser();
        LeaveRequest request = h.requireLeaveRequest(leaveId);
        h.ensureNotCancelled(request);
        LeaveApproval pending = h.requireCurrentPendingApproval(request);
        if (!LeaveServiceHelper.ACTION_APPROVE.equals(pending.getActionType())) {
            throw new BizException("当前节点不是审批节点，不能上传审批签名");
        }
        h.ensureCurrentActor(operator, request, pending);

        boolean signatureRequired = h.leaveSignRequirementService.isSignatureRequired(operator.getRoleCode(), request.getLeaveTypeId());
        if (!signatureRequired) {
            throw new BizException("当前审批节点不要求上传电子签名");
        }

        if (dto != null && dto.getSignatureFile() != null && !dto.getSignatureFile().isEmpty()) {
            throw new BizException("电子签名只能由超级管理员预先上传，审批时不允许临时上传");
        }

        String signatureUrl = h.normalizeSignatureUrl(operator.getSignatureUrl());
        if (signatureUrl == null || signatureUrl.isBlank()) {
            throw new BizException("当前账号未配置电子签名，无法审批");
        }
        return ApprovalSignatureUploadResponse.builder()
                .leaveId(leaveId)
                .stepNo(pending.getStepNo())
                .signatureUrl(signatureUrl)
                .build();
    }

    @Override
    @Transactional
    public LeaveDetailResponse uploadHandwrittenSignature(Long leaveId, HandwrittenSignatureDto dto) {
        LeaveRequest request = h.requireLeaveRequest(leaveId);
        String type = dto.getApplicantType().trim().toUpperCase();
        if (!"APPLICANT".equals(type) && !"TEAM_LEADER".equals(type) && !"APPLICANT_DATE".equals(type)) {
            throw new BizException("签名类型只能是 APPLICANT、TEAM_LEADER 或 APPLICANT_DATE，分别为请假人姓名、班组长姓名、请假人日期");
        }

        if (dto.getSignatureFile() == null || dto.getSignatureFile().isEmpty()) {
            throw new BizException("签名文件不能为空");
        }
        try {
            String url = h.saveHandwrittenSignatureFile(leaveId, type, dto.getSignatureFile().getOriginalFilename(),
                    dto.getSignatureFile().getInputStream());
            if ("APPLICANT".equals(type)) {
                h.leaveRequestMapper.updateApplicantSignatureUrl(leaveId, url);
                request.setApplicantSignatureUrl(url);
            } else if ("APPLICANT_DATE".equals(type)) {
                h.leaveRequestMapper.updateApplicantDateSignatureUrl(leaveId, url);
                request.setApplicantDateSignatureUrl(url);
            } else {
                h.leaveRequestMapper.updateTeamLeaderSignatureUrlOnly(leaveId, url);
                request.setTeamLeaderSignatureUrl(url);
            }
            return leaveQueryService.getLeaveDetail(leaveId);
        } catch (IOException ex) {
            throw new BizException("手写签名上传失败");
        }
    }

    @Override
    @Transactional
    public LeaveDetailResponse uploadTeamLeaderSignatureDate(Long leaveId, LocalDate signatureDate) {
        LeaveRequest request = h.requireLeaveRequest(leaveId);
        h.leaveRequestMapper.updateTeamLeaderSignatureDate(leaveId, signatureDate);
        request.setTeamLeaderSignatureDate(signatureDate);
        return leaveQueryService.getLeaveDetail(leaveId);
    }

    @Override
    @Transactional
    public LeaveDetailResponse selectApprovers(Long leaveId, SelectApproversDto dto) {
        UserAccount operator = h.requireCurrentUser();
        LeaveRequest request = h.requireLeaveRequest(leaveId);
        h.ensureNotCancelled(request);
        LeaveApproval pending = h.requireCurrentPendingApproval(request);
        if (!LeaveServiceHelper.ACTION_SELECT.equals(pending.getActionType())) {
            throw new BizException("当前节点不是选择审批人节点");
        }
        h.ensureCurrentActor(operator, request, pending);

        ApprovalRuleStep currentRuleStep = h.requireRuleStep(request.getApprovalRuleId(), pending.getRuleStepId());
        if (dto.getApproverUserIds().size() != currentRuleStep.getAssigneeCount()) {
            throw new BizException("选择审批人数不正确");
        }

        List<UserAccount> selectedUsers = h.validateAndResolveSelectedApprovers(request, pending, currentRuleStep, dto.getApproverUserIds());
        h.decideApproval(pending, operator.getId(), true, dto.getComment(), null, null, null);
        List<LeaveApproval> targets = h.resolveOrCreateSelectedApprovalTargets(request, currentRuleStep, selectedUsers);

        Map<String, UserAccount> userByRole = selectedUsers.stream()
                .collect(Collectors.toMap(UserAccount::getRoleCode, u -> u, (left, right) -> left));
        for (LeaveApproval target : targets) {
            UserAccount user = userByRole.get(target.getApproverRoleCode());
            if (user != null) {
                target.setApproverUserId(user.getId());
                h.leaveApprovalMapper.updateApprover(target);
            }
        }
        h.swapApprovalStepNoIfNeeded(request, targets);
        h.moveToNextStep(request);
        return leaveQueryService.getLeaveDetail(leaveId);
    }

    @Override
    @Transactional
    public LeaveDetailResponse reSelectApprovers(Long leaveId, SelectApproversDto dto) {
        UserAccount operator = h.requireCurrentUser();
        LeaveRequest request = h.requireLeaveRequest(leaveId);
        h.ensureNotCancelled(request);
        if (!LeaveRequestStatus.APPROVING.equals(request.getStatus())) {
            throw new BizException("当前请假单状态不允许重选领导");
        }

        List<LeaveApproval> allApprovals = h.leaveApprovalMapper.findByLeaveRequestId(leaveId);

        LeaveApproval selectApproval = allApprovals.stream()
                .filter(a -> LeaveServiceHelper.ACTION_SELECT.equals(a.getActionType())
                        && ApprovalStatus.APPROVED.equals(a.getApprovalStatus())
                        && a.getApproverUserId() != null
                        && a.getApproverUserId().equals(operator.getId()))
                .max(Comparator.comparing(LeaveApproval::getStepNo))
                .orElseThrow(() -> new BizException("未找到已审批的选择领导节点，不能重选"));

        List<LeaveApproval> pendingApproveAfterSelect = allApprovals.stream()
                .filter(a -> a.getStepNo() > selectApproval.getStepNo()
                        && ApprovalStatus.PENDING.equals(a.getApprovalStatus())
                        && LeaveServiceHelper.ACTION_APPROVE.equals(a.getActionType()))
                .collect(Collectors.toList());

        if (pendingApproveAfterSelect.isEmpty()) {
            throw new BizException("后续领导已审批，不能重选");
        }

        ApprovalRuleStep selectRuleStep = h.requireRuleStep(request.getApprovalRuleId(), selectApproval.getRuleStepId());
        if (dto.getApproverUserIds().size() != selectRuleStep.getAssigneeCount()) {
            throw new BizException("选择审批人数不正确");
        }

        LeaveApproval virtualPending = new LeaveApproval();
        virtualPending.setStepNo(selectApproval.getStepNo());
        virtualPending.setRuleStepId(selectApproval.getRuleStepId());
        virtualPending.setActionType(LeaveServiceHelper.ACTION_SELECT);
        List<UserAccount> selectedUsers = h.validateAndResolveSelectedApprovers(request, virtualPending, selectRuleStep, dto.getApproverUserIds());

        h.leaveApprovalMapper.deletePendingAfterStep(leaveId, selectApproval.getStepNo());

        selectApproval.setApprovalStatus(ApprovalStatus.PENDING);
        selectApproval.setApproverUserId(operator.getId());
        selectApproval.setApprovalComment(dto.getComment());
        selectApproval.setSignatureUrl(null);
        selectApproval.setApprovedAt(null);
        h.leaveApprovalMapper.updateDecision(selectApproval);

        List<LeaveApproval> newTargets = h.resolveOrCreateSelectedApprovalTargets(request, selectRuleStep, selectedUsers);
        Map<String, UserAccount> userByRole = selectedUsers.stream()
                .collect(Collectors.toMap(UserAccount::getRoleCode, u -> u, (left, right) -> left));
        for (LeaveApproval target : newTargets) {
            UserAccount user = userByRole.get(target.getApproverRoleCode());
            if (user != null) {
                target.setApproverUserId(user.getId());
                h.leaveApprovalMapper.updateApprover(target);
            }
        }
        h.swapApprovalStepNoIfNeeded(request, newTargets);

        request.setStatus(LeaveRequestStatus.APPROVING);
        request.setCurrentStep(selectApproval.getStepNo());
        request.setCurrentActionType(LeaveServiceHelper.ACTION_SELECT);
        request.setCurrentApproverId(operator.getRoleCode());
        request.setFinalApprovedAt(null);
        h.leaveRequestMapper.updateApprovalState(request);

        return leaveQueryService.getLeaveDetail(leaveId);
    }

    // ==================== 私有方法 ====================

    private LeaveDetailResponse approveInternal(UserAccount operator, Long leaveId, Boolean approved, String comment,
                                                 org.springframework.web.multipart.MultipartFile signatureFile,
                                                 String signatureUrl, LeaveServiceHelper.BatchSignaturePayload batchSignaturePayload,
                                                 LocalDate signatureDate, LocalDateTime approvedAt) {
        LeaveRequest request = h.requireLeaveRequest(leaveId);
        h.ensureNotCancelled(request);
        LeaveApproval pending = h.requireCurrentPendingApproval(request);
        if (!LeaveRequestStatus.PENDING.equals(request.getStatus())
                && !LeaveRequestStatus.APPROVING.equals(request.getStatus())) {
            throw new BizException("当前请假单状态不允许审批");
        }
        if (!LeaveServiceHelper.ACTION_APPROVE.equals(pending.getActionType())) {
            throw new BizException("当前节点不是审批节点");
        }
        h.ensureCurrentActor(operator, request, pending);

        String finalSignatureUrl = h.normalizeSignatureUrl(operator.getSignatureUrl());
        boolean signatureRequired = h.leaveSignRequirementService.isSignatureRequired(operator.getRoleCode(), request.getLeaveTypeId());
        h.ensureNoTemporarySignatureOverride(signatureFile, signatureUrl,
                batchSignaturePayload == null ? null : batchSignaturePayload.bytes(), finalSignatureUrl);
        if (signatureRequired) {
            if (finalSignatureUrl == null || finalSignatureUrl.isBlank()) {
                throw new BizException("当前账号未配置电子签名，无法审批");
            }
        }

        h.decideApproval(pending, operator.getId(), approved, comment, finalSignatureUrl, signatureDate, approvedAt);

        if (Boolean.FALSE.equals(approved)) {
            request.setStatus(LeaveRequestStatus.REJECTED);
            request.setCurrentStep(pending.getStepNo());
            request.setCurrentActionType(pending.getActionType());
            h.leaveRequestMapper.updateApprovalState(request);
            return leaveQueryService.getLeaveDetail(leaveId);
        }

        h.moveToNextStep(request);
        return leaveQueryService.getLeaveDetail(leaveId);
    }
}
