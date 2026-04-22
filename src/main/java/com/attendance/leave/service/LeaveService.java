package com.attendance.leave.service;

import com.attendance.admin.mapper.ApprovalPermissionMapper;
import com.attendance.admin.model.ApprovalPermission;
import com.attendance.auth.security.CurrentUser;
import com.attendance.auth.security.UserContext;
import com.attendance.common.PageResponse;
import com.attendance.exception.BizException;
import com.attendance.leave.dto.ApprovalRecordResponse;
import com.attendance.leave.dto.ApprovalSignatureUploadResponse;
import com.attendance.leave.dto.ApproveLeaveWithSignatureDto;
import com.attendance.leave.dto.BatchApproveLeaveDto;
import com.attendance.leave.dto.BatchApproveLeaveResponse;
import com.attendance.leave.dto.CreateLeaveRequestDto;
import com.attendance.leave.dto.CancelLeaveRequestDto;
import com.attendance.leave.dto.LeaveDetailResponse;
import com.attendance.leave.dto.LeaveStatusOptionResponse;
import com.attendance.leave.dto.LeaveListItemResponse;
import com.attendance.leave.dto.LeavePdfResponse;
import com.attendance.leave.dto.PendingSummaryResponse;
import com.attendance.leave.dto.SelectedApproverResponse;
import com.attendance.leave.dto.SelectApproversDto;
import com.attendance.leave.dto.UploadApprovalSignatureDto;
import com.attendance.leave.enums.ApprovalStatus;
import com.attendance.leave.enums.ApprovalStep;
import com.attendance.leave.enums.LeaveRequestStatus;
import com.attendance.leave.enums.RoleCode;
import com.attendance.leave.mapper.ApprovalRuleMapper;
import com.attendance.leave.mapper.ApprovalRuleStepMapper;
import com.attendance.leave.mapper.LeaveApprovalMapper;
import com.attendance.leave.mapper.LeaveRequestMapper;
import com.attendance.leave.mapper.LeaveTypeMapper;
import com.attendance.leave.mapper.UserAccountMapper;
import com.attendance.leave.model.ApprovalRule;
import com.attendance.leave.model.ApprovalRuleStep;
import com.attendance.leave.model.LeaveApproval;
import com.attendance.leave.model.LeaveRequest;
import com.attendance.leave.model.LeaveType;
import com.attendance.leave.model.UserAccount;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private static final String FILE_URL_PREFIX = "/files/";
    private static final String LEGACY_FILE_HOST = "http://192.168.1.10:8080";

    private static final String APPLICANT_TYPE_EMPLOYEE = "EMPLOYEE";
    private static final String APPLICANT_TYPE_CADRE = "CADRE";
    private static final String APPLICANT_TYPE_GENERAL_CADRE = "GENERAL_CADRE";
    private static final String APPLICANT_TYPE_SECTION_LEVEL_CADRE = "SECTION_LEVEL_CADRE";
    private static final String POSITION_STAFF = "STAFF";
    private static final String POSITION_GENERAL_CADRE = "GENERAL_CADRE";
    private static final String POSITION_SECTION_LEVEL = "SECTION_LEVEL";
    private static final String LEAVE_SCOPE_ALL = "ALL";
    private static final String LEAVE_SCOPE_OTHER = "OTHER";
    private static final String LEAVE_SCOPE_SICK = "SICK";
    private static final String LEAVE_SCOPE_PERSONAL = "PERSONAL";
    private static final String ACTION_APPROVE = "APPROVE";
    private static final String ACTION_SELECT = "SELECT";
    private static final String APPROVER_SOURCE_APPLICANT_ORG = "APPLICANT_ORG";
    private static final String APPROVER_SOURCE_HR_ORG = "HR_ORG";
    private static final String APPROVER_SOURCE_SELECTED = "SELECTED";
    private static final String CANDIDATE_GROUP_SUPERVISOR = "SUPERVISOR_LEADER";
    private static final String CANDIDATE_GROUP_STATIONMASTER = "STATIONMASTER";
    private static final String CANDIDATE_GROUP_UNIT_LEADER = "UNIT_LEADER";
    private static final String CANDIDATE_GROUP_PARTY_AND_PRINCIPAL = "PARTY_AND_PRINCIPAL";
    private static final BigDecimal DAY_1 = BigDecimal.ONE;
    private static final BigDecimal DAY_2 = BigDecimal.valueOf(2);
    private static final BigDecimal DAY_5 = BigDecimal.valueOf(5);
    private static final BigDecimal DAY_7 = BigDecimal.valueOf(7);
    private static final BigDecimal DAY_10 = BigDecimal.valueOf(10);
    private static final BigDecimal DAY_30 = BigDecimal.valueOf(30);
    private static final BigDecimal DAY_60 = BigDecimal.valueOf(60);
    private static final List<String> EFFECTIVE_LEAVE_STATUSES = List.of(
            LeaveRequestStatus.PENDING,
            LeaveRequestStatus.APPROVING,
            LeaveRequestStatus.APPROVED
    );

    private final UserAccountMapper userAccountMapper;
    private final LeaveTypeMapper leaveTypeMapper;
    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveApprovalMapper leaveApprovalMapper;
    private final ApprovalRuleMapper approvalRuleMapper;
    private final ApprovalRuleStepMapper approvalRuleStepMapper;
    private final LeaveSignRequirementService leaveSignRequirementService;
    private final ApprovalPermissionMapper approvalPermissionMapper;
    private final LeaveDocumentService leaveDocumentService;

    @Value("${attendance.file-storage-path:uploads}")
    private String fileStoragePath;

    @Transactional
    public LeaveDetailResponse createLeave(CreateLeaveRequestDto dto) {
        UserAccount operator = requireCurrentUser();
        UserAccount applicant = resolveApplicant(dto);
        LeaveType leaveType = requireLeaveType(dto.getLeaveTypeId());

        if (!RoleCode.ATTENDANCE_ADMIN.equals(operator.getRoleCode())) {
            throw new BizException("只有考勤管理员可以提交请假记录单");
        }
        if (!operator.getOrgUnitId().equals(applicant.getOrgUnitId())) {
            throw new BizException("考勤管理员只能提交本部门的请假记录单");
        }

        String applicantType = normalizeApplicantType(dto.getApplicantType(), applicant.getPositionLevelCode());
        BigDecimal allowedDays = leaveType.getDefaultDays() == null ? null : BigDecimal.valueOf(leaveType.getDefaultDays());
        boolean exceedsOneMonth = dto.getLeaveDays() != null && dto.getLeaveDays().compareTo(BigDecimal.valueOf(30)) > 0;
        validateLeaveRequestRules(applicant, leaveType, dto);
        ApprovalRule rule = resolveApprovalRule(applicantType, applicant.getPositionLevelCode(), leaveType, dto.getLeaveDays(), exceedsOneMonth);
        List<ApprovalRuleStep> steps = requireRuleSteps(rule.getId());

        LeaveRequest request = new LeaveRequest();
        request.setRequestNo(generateRequestNo());
        request.setApplicantId(applicant.getId());
        request.setOrgUnitId(applicant.getOrgUnitId());
        request.setLeaveTypeId(leaveType.getId());
        request.setApprovalRuleId(rule.getId());
        request.setApplicantNameSnapshot(applicant.getEmpName());
        request.setApplicantType(applicantType);
        request.setPositionLevelCode(resolvePositionLevel(applicantType, applicant.getPositionLevelCode()));
        request.setJobTitleSnapshot(dto.getJobTitle().trim());
        request.setTeamLeaderSnapshot(dto.getTeamLeaderInfo().trim());
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
        request.setSubmittedAt(LocalDateTime.now());
        request.setCreatedBy(operator.getId());
        leaveRequestMapper.insert(request);

        for (ApprovalRuleStep step : steps) {
            UserAccount approver = resolveInitialApprover(applicant.getOrgUnitId(), step);
            leaveApprovalMapper.insert(buildApproval(request.getId(), step, approver == null ? null : approver.getId()));
        }

        return getLeaveDetail(request.getId());
    }

    @Transactional
    public LeaveDetailResponse approve(Long leaveId, ApproveLeaveWithSignatureDto dto) {
        UserAccount operator = requireCurrentUser();
        return approveInternal(operator, leaveId, dto.getApproved(), dto.getComment(), dto.getSignatureFile(), dto.getSignatureUrl(), null);
    }

    @Transactional
    public BatchApproveLeaveResponse batchApprove(BatchApproveLeaveDto dto) {
        UserAccount operator = requireCurrentUser();
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

        List<LeaveDetailResponse> records = new ArrayList<>();
        for (Long leaveId : dto.getLeaveIds()) {
            records.add(approveInternal(operator, leaveId, dto.getApproved(), dto.getComment(),
                    null, dto.getSignatureUrl(), signatureBytes == null ? null : new BatchSignaturePayload(signatureBytes, originalFilename)));
        }
        return BatchApproveLeaveResponse.builder()
                .approvedCount(records.size())
                .records(records)
                .build();
    }

    public ApprovalSignatureUploadResponse uploadApprovalSignature(Long leaveId, UploadApprovalSignatureDto dto) {
        UserAccount operator = requireCurrentUser();
        LeaveRequest request = requireLeaveRequest(leaveId);
        ensureNotCancelled(request);
        LeaveApproval pending = requirePendingApproval(leaveId);
        if (!ACTION_APPROVE.equals(pending.getActionType())) {
            throw new BizException("当前节点不是审批节点，不能上传审批签名");
        }
        ensureCurrentActor(operator, request, pending);

        boolean signatureRequired = leaveSignRequirementService.isSignatureRequired(operator.getRoleCode(), request.getLeaveTypeId());
        if (!signatureRequired) {
            throw new BizException("当前审批节点不要求上传电子签名");
        }

        try {
            String signatureUrl = saveSignatureFile(
                    leaveId,
                    pending.getStepNo(),
                    dto.getSignatureFile().getOriginalFilename(),
                    dto.getSignatureFile().getInputStream()
            );
            return ApprovalSignatureUploadResponse.builder()
                    .leaveId(leaveId)
                    .stepNo(pending.getStepNo())
                    .signatureUrl(signatureUrl)
                    .build();
        } catch (IOException ex) {
            throw new BizException("电子签名上传失败");
        }
    }

    @Transactional
    public LeaveDetailResponse selectApprovers(Long leaveId, SelectApproversDto dto) {
        UserAccount operator = requireCurrentUser();
        LeaveRequest request = requireLeaveRequest(leaveId);
        ensureNotCancelled(request);
        LeaveApproval pending = requirePendingApproval(leaveId);
        if (!ACTION_SELECT.equals(pending.getActionType())) {
            throw new BizException("当前节点不是选择审批人节点");
        }
        ensureCurrentActor(operator, request, pending);

        ApprovalRuleStep currentRuleStep = requireRuleStep(request.getApprovalRuleId(), pending.getRuleStepId());
        if (dto.getApproverUserIds().size() != currentRuleStep.getAssigneeCount()) {
            throw new BizException("选择审批人数不正确");
        }

        List<UserAccount> selectedUsers = validateAndResolveSelectedApprovers(request, pending, currentRuleStep, dto.getApproverUserIds());

        decideApproval(pending, operator.getId(), true, dto.getComment(), null);

        List<LeaveApproval> targets = resolveOrCreateSelectedApprovalTargets(request, currentRuleStep, selectedUsers.size());

        for (int i = 0; i < selectedUsers.size(); i++) {
            LeaveApproval target = targets.get(i);
            target.setApproverUserId(selectedUsers.get(i).getId());
            leaveApprovalMapper.updateApprover(target);
        }

        moveToNextStep(request);
        return getLeaveDetail(leaveId);
    }

    private List<LeaveApproval> resolveOrCreateSelectedApprovalTargets(LeaveRequest request,
                                                                       ApprovalRuleStep currentRuleStep,
                                                                       int targetCount) {
        List<ApprovalRuleStep> selectedSteps = requireRuleSteps(request.getApprovalRuleId()).stream()
                .filter(step -> step.getStepNo() > currentRuleStep.getStepNo())
                .filter(step -> ACTION_APPROVE.equals(step.getActionType()))
                .filter(step -> APPROVER_SOURCE_SELECTED.equals(step.getApproverSource()))
                .filter(step -> currentRuleStep.getCandidateGroup() == null
                        || currentRuleStep.getCandidateGroup().equals(step.getCandidateGroup()))
                .limit(targetCount)
                .collect(Collectors.toList());
        if (selectedSteps.size() < targetCount) {
            throw new BizException("后续领导审批节点配置不完整");
        }

        List<LeaveApproval> approvals = leaveApprovalMapper.findByLeaveRequestId(request.getId());
        List<LeaveApproval> targets = new ArrayList<>();
        for (ApprovalRuleStep step : selectedSteps) {
            LeaveApproval target = approvals.stream()
                    .filter(item -> item.getRuleStepId().equals(step.getId()))
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                target = buildApproval(request.getId(), step, null);
                leaveApprovalMapper.insert(target);
            }
            if (!ApprovalStatus.PENDING.equals(target.getApprovalStatus())) {
                throw new BizException("后续领导审批节点状态异常");
            }
            targets.add(target);
        }
        return targets;
    }

    @Transactional
    public LeaveDetailResponse cancelLeave(Long leaveId, CancelLeaveRequestDto dto) {
        UserAccount operator = requireCurrentUser();
        LeaveRequest request = requireLeaveRequest(leaveId);
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

        String cancelComment = buildCancelComment(dto);
        request.setStatus(LeaveRequestStatus.CANCELLED);
        request.setCurrentActionType(null);
        leaveRequestMapper.updateApprovalState(request);
        leaveApprovalMapper.cancelPendingByLeaveRequestId(leaveId, cancelComment);
        return getLeaveDetail(leaveId);
    }

    public LeaveDetailResponse getLeaveDetail(Long leaveId) {
        LeaveRequest request = requireLeaveRequest(leaveId);
        UserAccount applicant = requireUser(request.getApplicantId());
        LeaveType leaveType = requireLeaveType(request.getLeaveTypeId());
        List<ApprovalRecordResponse> approvals = leaveApprovalMapper.findByLeaveRequestId(leaveId).stream()
                .map(this::toApprovalRecordResponse)
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
                .pdfUrl(pdfUrl)
                .approvals(approvals)
                .build();
    }

    public LeavePdfResponse getLeavePdf(Long leaveId) {
        LeaveDetailResponse detail = getLeaveDetail(leaveId);
        return LeavePdfResponse.builder()
                .pdfUrl(detail.getPdfUrl())
                .build();
    }

    public List<SelectedApproverResponse> getSelectedApprovers(Long leaveId) {
        LeaveRequest request = requireLeaveRequest(leaveId);
        LeaveApproval pending = requirePendingApproval(leaveId);
        if (!ACTION_SELECT.equals(pending.getActionType())) {
            return List.of();
        }
        ApprovalRuleStep currentRuleStep = requireRuleStep(request.getApprovalRuleId(), pending.getRuleStepId());
        return resolveSelectableApprovers(request, pending, currentRuleStep);
    }

    public PageResponse<LeaveListItemResponse> listLeaves(String status, Long leaveTypeId, Integer pageNum, Integer pageSize) {
        UserAccount operator = requireCurrentUser();
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

        Long total;
        List<LeaveListItemResponse> records;
        if (shouldUsePendingApproverView(operator)) {
            total = leaveRequestMapper.countByResponsibleApprover(
                    operator.getId(), operator.getRoleCode(), operator.getOrgUnitId(), normalizedStatus, leaveTypeId);
            records = leaveRequestMapper.findPageByResponsibleApprover(
                            operator.getId(), operator.getRoleCode(), operator.getOrgUnitId(), normalizedStatus, leaveTypeId, offset, safePageSize)
                    .stream()
                    .map(this::toLeaveListItemResponse)
                    .collect(Collectors.toList());
        } else {
            total = leaveRequestMapper.countByScope(orgUnitId, applicantId, normalizedStatus, leaveTypeId);
            records = leaveRequestMapper
                    .findPageByScope(orgUnitId, applicantId, normalizedStatus, leaveTypeId, offset, safePageSize)
                    .stream()
                    .map(this::toLeaveListItemResponse)
                    .collect(Collectors.toList());
        }
        return PageResponse.<LeaveListItemResponse>builder()
                .total(total == null ? 0L : total)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .records(records)
                .build();
    }

    public PendingSummaryResponse getPendingSummary() {
        UserAccount operator = requireCurrentUser();
        Long count = leaveApprovalMapper.countPendingForUser(operator.getId(), operator.getRoleCode(), operator.getOrgUnitId());
        return PendingSummaryResponse.builder()
                .pendingCount(count == null ? 0L : count)
                .build();
    }

    public List<LeaveType> listLeaveTypes() {
        return leaveTypeMapper.findAll();
    }

    public List<LeaveStatusOptionResponse> listLeaveStatuses() {
        UserAccount operator = requireCurrentUser();
        if (RoleCode.ATTENDANCE_ADMIN.equals(operator.getRoleCode())) {
            return List.of(
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.PENDING).name("待审批").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.APPROVING).name("审批中").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.APPROVED).name("已通过").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.REJECTED).name("已驳回").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.CANCELLED).name("已撤销").build()
            );
        }
        if (RoleCode.ORG_PRINCIPAL.equals(operator.getRoleCode())) {
            return List.of(
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.PENDING).name("待审批").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.APPROVING).name("审批中").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.APPROVED).name("已通过").build(),
                    LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.REJECTED).name("已驳回").build()
            );
        }
        return List.of(
                LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.APPROVING).name("审批中").build(),
                LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.APPROVED).name("已通过").build(),
                LeaveStatusOptionResponse.builder().code(LeaveRequestStatus.REJECTED).name("已驳回").build()
        );
    }

    private LeaveListItemResponse toLeaveListItemResponse(LeaveRequest request) {
        LeaveType leaveType = requireLeaveType(request.getLeaveTypeId());
        return LeaveListItemResponse.builder()
                .id(request.getId())
                .requestNo(request.getRequestNo())
                .status(request.getStatus())
                .currentStep(request.getCurrentStep())
                .currentActionType(request.getCurrentActionType())
                .applicantId(request.getApplicantId())
                .applicantName(request.getApplicantNameSnapshot())
                .applicantType(request.getApplicantType())
                .orgUnitId(request.getOrgUnitId())
                .leaveTypeId(request.getLeaveTypeId())
                .leaveTypeName(leaveType.getLeaveName())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .leaveDays(request.getLeaveDays())
                .teamLeaderSnapshot(request.getTeamLeaderSnapshot())
                .reason(request.getReason())
                .remark(request.getRemark())
                .submittedAt(request.getSubmittedAt())
                .build();
    }

    private void ensureNotCancelled(LeaveRequest request) {
        if (LeaveRequestStatus.CANCELLED.equals(request.getStatus())) {
            throw new BizException("请假单已取消，不能继续处理");
        }
    }

    private LeaveDetailResponse approveInternal(UserAccount operator, Long leaveId, Boolean approved, String comment,
                                                org.springframework.web.multipart.MultipartFile signatureFile,
                                                String signatureUrl, BatchSignaturePayload batchSignaturePayload) {
        LeaveRequest request = requireLeaveRequest(leaveId);
        ensureNotCancelled(request);
        LeaveApproval pending = requirePendingApproval(leaveId);
        if (!ACTION_APPROVE.equals(pending.getActionType())) {
            throw new BizException("当前节点不是审批节点");
        }
        ensureCurrentActor(operator, request, pending);

        boolean signatureRequired = leaveSignRequirementService.isSignatureRequired(operator.getRoleCode(), request.getLeaveTypeId());
        String finalSignatureUrl = normalizeSignatureUrl(signatureUrl);
        if (signatureRequired) {
            boolean noUploadFile = (signatureFile == null || signatureFile.isEmpty()) && batchSignaturePayload == null;
            if (noUploadFile && (finalSignatureUrl == null || finalSignatureUrl.isBlank())) {
                throw new BizException("当前审批节点必须上传电子签名");
            }
        }
        if (signatureFile != null && !signatureFile.isEmpty()) {
            try {
                finalSignatureUrl = saveSignatureFile(leaveId, pending.getStepNo(),
                        signatureFile.getOriginalFilename(), signatureFile.getInputStream());
            } catch (IOException ex) {
                throw new BizException("电子签名上传失败");
            }
        } else if (batchSignaturePayload != null) {
            try {
                finalSignatureUrl = saveSignatureFile(leaveId, pending.getStepNo(),
                        batchSignaturePayload.originalFilename(), new java.io.ByteArrayInputStream(batchSignaturePayload.bytes()));
            } catch (IOException ex) {
                throw new BizException("电子签名上传失败");
            }
        }

        decideApproval(pending, operator.getId(), approved, comment, finalSignatureUrl);

        if (Boolean.FALSE.equals(approved)) {
            request.setStatus(LeaveRequestStatus.REJECTED);
            request.setCurrentStep(pending.getStepNo());
            request.setCurrentActionType(pending.getActionType());
            leaveRequestMapper.updateApprovalState(request);
            return getLeaveDetail(leaveId);
        }

        moveToNextStep(request);
        return getLeaveDetail(leaveId);
    }

    private boolean shouldUsePendingApproverView(UserAccount operator) {
        return !RoleCode.ATTENDANCE_ADMIN.equals(operator.getRoleCode())
                && !RoleCode.ORG_PRINCIPAL.equals(operator.getRoleCode())
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

        if (RoleCode.ORG_PRINCIPAL.equals(operator.getRoleCode())) {
            if (!List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVING,
                    LeaveRequestStatus.APPROVED, LeaveRequestStatus.REJECTED).contains(status)) {
                throw new BizException("科室车间负责人仅可查询待审批、审批中、已通过、已驳回状态");
            }
            return status;
        }

        if (!List.of(LeaveRequestStatus.APPROVING, LeaveRequestStatus.APPROVED,
                LeaveRequestStatus.REJECTED).contains(status)) {
            throw new BizException("当前角色仅可查询审批中、已通过、已驳回状态");
        }
        return status;
    }

    private void moveToNextStep(LeaveRequest request) {
        LeaveApproval nextPending = leaveApprovalMapper.findFirstPending(request.getId());
        if (nextPending == null) {
            request.setStatus(LeaveRequestStatus.APPROVED);
            request.setCurrentStep(ApprovalStep.FINISHED);
            request.setCurrentActionType(null);
            request.setFinalApprovedAt(LocalDateTime.now());
        } else {
            request.setStatus(LeaveRequestStatus.APPROVING);
            request.setCurrentStep(nextPending.getStepNo());
            request.setCurrentActionType(nextPending.getActionType());
        }
        leaveRequestMapper.updateApprovalState(request);
    }

    private ApprovalRule resolveApprovalRule(String applicantType, String actualPositionLevel, LeaveType leaveType, BigDecimal leaveDays, boolean exceedsOneMonth) {
        String positionLevel = resolvePositionLevel(applicantType, actualPositionLevel);
        String leaveScope = resolveLeaveScope(leaveType);
        String ruleApplicantType = resolveRuleApplicantType(applicantType);

        return approvalRuleMapper.findActiveRules(ruleApplicantType, positionLevel).stream()
                .filter(rule -> matchesScope(rule.getLeaveScope(), leaveScope))
                .filter(rule -> matchesDays(rule, leaveDays))
                .filter(rule -> matchesExceedsMonth(rule, exceedsOneMonth))
                .filter(rule -> matchesPersonalLeaveRuleName(rule, leaveDays, leaveScope))
                .sorted(Comparator.comparing(ApprovalRule::getId))
                .findFirst()
                .orElseThrow(() -> new BizException("未找到匹配的审批规则：applicantType=" + ruleApplicantType
                        + ", positionLevel=" + positionLevel
                        + ", leaveScope=" + leaveScope
                        + ", leaveDays=" + leaveDays
                        + ", exceedsOneMonth=" + exceedsOneMonth));
    }

    private boolean matchesPersonalLeaveRuleName(ApprovalRule rule, BigDecimal leaveDays, String leaveScope) {
        if (!LEAVE_SCOPE_PERSONAL.equals(leaveScope) || leaveDays == null) {
            return true;
        }
        if (!APPLICANT_TYPE_EMPLOYEE.equals(rule.getApplicantType())) {
            return true;
        }
        if (leaveDays.compareTo(DAY_1) <= 0) {
            return "EMPLOYEE_PERSONAL_WITHIN_5".equals(rule.getRuleCode());
        }
        if (leaveDays.compareTo(DAY_2) >= 0 && leaveDays.compareTo(DAY_5) < 0) {
            return "EMPLOYEE_PERSONAL_WITHIN_5".equals(rule.getRuleCode());
        }
        return true;
    }

    private String resolvePositionLevel(String applicantType, String actualPositionLevel) {
        if (APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(applicantType)) {
            return POSITION_SECTION_LEVEL;
        }
        if (APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType)) {
            return POSITION_GENERAL_CADRE;
        }
        if (APPLICANT_TYPE_CADRE.equals(applicantType) && POSITION_SECTION_LEVEL.equals(actualPositionLevel)) {
            return POSITION_SECTION_LEVEL;
        }
        return APPLICANT_TYPE_CADRE.equals(applicantType) ? POSITION_GENERAL_CADRE : POSITION_STAFF;
    }

    private String normalizeApplicantType(String applicantType, String actualPositionLevel) {
        if (applicantType == null || applicantType.isBlank()) {
            throw new BizException("人员类别不能为空");
        }
        if (APPLICANT_TYPE_EMPLOYEE.equalsIgnoreCase(applicantType)) {
            return APPLICANT_TYPE_EMPLOYEE;
        }
        if (APPLICANT_TYPE_CADRE.equalsIgnoreCase(applicantType)) {
            return POSITION_SECTION_LEVEL.equals(actualPositionLevel)
                    ? APPLICANT_TYPE_SECTION_LEVEL_CADRE
                    : APPLICANT_TYPE_GENERAL_CADRE;
        }
        if (APPLICANT_TYPE_GENERAL_CADRE.equalsIgnoreCase(applicantType)) {
            if (POSITION_SECTION_LEVEL.equals(actualPositionLevel)) {
                throw new BizException("该人员岗位级别为中层正职，请选择中层正职");
            }
            return APPLICANT_TYPE_GENERAL_CADRE;
        }
        System.out.println(actualPositionLevel);
        System.out.println(POSITION_SECTION_LEVEL.equals(actualPositionLevel));
        if (APPLICANT_TYPE_SECTION_LEVEL_CADRE.equalsIgnoreCase(applicantType)
                || POSITION_SECTION_LEVEL.equalsIgnoreCase(applicantType)) {
            if (!POSITION_SECTION_LEVEL.equals(actualPositionLevel)) {
                throw new BizException("该人员不是中层正职，不能选择中层正职");
            }
            return APPLICANT_TYPE_SECTION_LEVEL_CADRE;
        }
        throw new BizException("人员类别只能是 职工、一般干部、中层正职");
    }

    private boolean matchesScope(String ruleScope, String actualScope) {
        return LEAVE_SCOPE_ALL.equals(ruleScope) || ruleScope.equals(actualScope);
    }

    private boolean matchesDays(ApprovalRule rule, BigDecimal leaveDays) {
        if (leaveDays == null) {
            return true;
        }
        if (rule.getMinDays() != null && leaveDays.compareTo(rule.getMinDays()) < 0) {
            return false;
        }
        return rule.getMaxDays() == null || leaveDays.compareTo(rule.getMaxDays()) <= 0;
    }

    private boolean matchesExceedsMonth(ApprovalRule rule, boolean exceedsOneMonth) {
        if (LEAVE_SCOPE_ALL.equals(rule.getLeaveScope())) {
            return true;
        }
        return exceedsOneMonth == (rule.getExceedsMonthOnly() != null && rule.getExceedsMonthOnly() == 1)
                || (!exceedsOneMonth && (rule.getExceedsMonthOnly() == null || rule.getExceedsMonthOnly() == 0));
    }

    private String resolveLeaveScope(LeaveType leaveType) {
        if ("病".equals(leaveType.getLeaveCode())) {
            return LEAVE_SCOPE_SICK;
        }
        if ("事".equals(leaveType.getLeaveCode())) {
            return LEAVE_SCOPE_PERSONAL;
        }
        return LEAVE_SCOPE_OTHER;
    }

    private void validateLeaveRequestRules(UserAccount applicant, LeaveType leaveType, CreateLeaveRequestDto dto) {
        if (dto.getStartTime().isAfter(dto.getEndTime())) {
            throw new BizException("结束时间不能早于开始时间");
        }
        if (!LEAVE_SCOPE_PERSONAL.equals(resolveLeaveScope(leaveType))) {
            return;
        }
        validatePersonalLeaveRules(applicant, dto);
    }

    private void validatePersonalLeaveRules(UserAccount applicant, CreateLeaveRequestDto dto) {
        BigDecimal leaveDays = dto.getLeaveDays();
        if (leaveDays == null) {
            return;
        }

        if (leaveDays.compareTo(DAY_30) >= 0 && leaveDays.compareTo(DAY_60) > 0) {
            throw new BizException("特殊情况单次事假原则上不得超过2个月");
        }

        java.time.LocalDate startDate = dto.getStartTime().toLocalDate();
        java.time.LocalDate endDate = dto.getEndTime().toLocalDate();
        java.time.LocalDate monthStart = startDate.withDayOfMonth(1);
        java.time.LocalDate monthEnd = startDate.withDayOfMonth(startDate.lengthOfMonth());
        java.time.LocalDate quarterStart = startDate.withMonth(firstMonthOfQuarter(startDate.getMonth()).getValue()).withDayOfMonth(1);
        java.time.LocalDate quarterEnd = quarterStart.plusMonths(2).withDayOfMonth(quarterStart.plusMonths(2).lengthOfMonth());
        java.time.LocalDate yearStart = startDate.withDayOfYear(1);
        java.time.LocalDate yearEnd = startDate.withDayOfYear(startDate.lengthOfYear());

        if (leaveDays.compareTo(DAY_1) <= 0) {
            long monthlyCount = countPersonalLeave(applicant.getId(), monthStart, monthEnd, null, DAY_1);
            if (monthlyCount >= 3) {
                throw new BizException("单次请事假1天以内的每月不得超过3次");
            }
            return;
        }

        if (leaveDays.compareTo(DAY_2) >= 0 && leaveDays.compareTo(DAY_5) < 0) {
            long monthlyCount = countPersonalLeave(applicant.getId(), monthStart, monthEnd, DAY_2, BigDecimal.valueOf(4.99));
            if (monthlyCount >= 2) {
                throw new BizException("单次请事假2天及以上至5天以内的每月不得超过2次");
            }
            long quarterCount = countPersonalLeave(applicant.getId(), quarterStart, quarterEnd, DAY_2, BigDecimal.valueOf(4.99));
            if (quarterCount >= 3) {
                throw new BizException("单次请事假2天及以上至5天以内的季度内不得超过3次");
            }
            ensureNoContinuousPersonalLeave(applicant.getId(), startDate, endDate);
            return;
        }

        if (leaveDays.compareTo(DAY_5) >= 0 && leaveDays.compareTo(DAY_10) < 0) {
            long yearlyCount = countPersonalLeave(applicant.getId(), yearStart, yearEnd, DAY_5, BigDecimal.valueOf(9.99));
            if (yearlyCount >= 3) {
                throw new BizException("单次请事假5天及以上至10天以内的年度内不得超过3次");
            }
            return;
        }

        if (leaveDays.compareTo(DAY_10) >= 0 && leaveDays.compareTo(DAY_30) < 0) {
            long yearlyCount = countPersonalLeave(applicant.getId(), yearStart, yearEnd, DAY_10, BigDecimal.valueOf(29.99));
            if (yearlyCount >= 3) {
                throw new BizException("单次请事假10天及以上至30天以内的年度内不得超过3次");
            }
            return;
        }

        if (leaveDays.compareTo(DAY_30) >= 0) {
            long yearlyCount = countPersonalLeave(applicant.getId(), yearStart, yearEnd, DAY_30, null);
            if (yearlyCount >= 2) {
                throw new BizException("单次请事假30天及以上的年度内不得超过2次");
            }
        }
    }

    private long countPersonalLeave(Long applicantId, java.time.LocalDate periodStart, java.time.LocalDate periodEnd,
                                    BigDecimal minDays, BigDecimal maxDays) {
        Long count = leaveRequestMapper.countLeaveRequestsByApplicantAndRange(
                applicantId,
                requirePersonalLeaveTypeId(),
                EFFECTIVE_LEAVE_STATUSES,
                periodStart,
                periodEnd,
                minDays,
                maxDays
        );
        return count == null ? 0L : count;
    }

    private void ensureNoContinuousPersonalLeave(Long applicantId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        LeaveRequest adjacent = leaveRequestMapper.findFirstOverlappingOrAdjacent(
                applicantId,
                requirePersonalLeaveTypeId(),
                EFFECTIVE_LEAVE_STATUSES,
                startDate.minusDays(1),
                endDate.plusDays(1)
        );
        if (adjacent != null) {
            throw new BizException("单次请事假2天及以上至5天以内的不得连续请休");
        }
    }

    private Long requirePersonalLeaveTypeId() {
        return leaveTypeMapper.findAll().stream()
                .filter(item -> "事".equals(item.getLeaveCode()))
                .findFirst()
                .map(LeaveType::getId)
                .orElseThrow(() -> new BizException("未配置事假假别"));
    }

    private Month firstMonthOfQuarter(Month month) {
        int firstMonth = ((month.getValue() - 1) / 3) * 3 + 1;
        return Month.of(firstMonth);
    }

    private List<ApprovalRuleStep> requireRuleSteps(Long ruleId) {
        List<ApprovalRuleStep> steps = approvalRuleStepMapper.findByRuleId(ruleId);
        if (steps.isEmpty()) {
            throw new BizException("审批规则未配置步骤");
        }
        return steps;
    }

    private ApprovalRuleStep requireRuleStep(Long ruleId, Long ruleStepId) {
        return requireRuleSteps(ruleId).stream()
                .filter(step -> step.getId().equals(ruleStepId))
                .findFirst()
                .orElseThrow(() -> new BizException("审批步骤不存在"));
    }

    private UserAccount resolveInitialApprover(Long applicantOrgUnitId, ApprovalRuleStep step) {
        if (APPROVER_SOURCE_APPLICANT_ORG.equals(step.getApproverSource())) {
            return userAccountMapper.findByOrgAndRole(applicantOrgUnitId, step.getApproverRoleCode());
        }
        if (APPROVER_SOURCE_HR_ORG.equals(step.getApproverSource())) {
            return userAccountMapper.findByRole(step.getApproverRoleCode());
        }
        if (APPROVER_SOURCE_SELECTED.equals(step.getApproverSource())) {
            return null;
        }
        return userAccountMapper.findByRole(step.getApproverRoleCode());
    }

    private List<UserAccount> validateAndResolveSelectedApprovers(LeaveRequest request,
                                                                  LeaveApproval pending,
                                                                  ApprovalRuleStep currentRuleStep,
                                                                  List<Long> approverUserIds) {
        List<SelectedApproverResponse> selectableApprovers = resolveSelectableApprovers(request, pending, currentRuleStep);
        if (selectableApprovers.isEmpty()) {
            throw new BizException("当前节点无可选领导");
        }

        Set<Long> allowedIds = selectableApprovers.stream()
                .map(SelectedApproverResponse::getApproverUserId)
                .collect(Collectors.toSet());
        Set<Long> selectedIdSet = Set.copyOf(approverUserIds);
        if (selectedIdSet.size() != approverUserIds.size()) {
            throw new BizException("所选领导不能重复");
        }
        if (!allowedIds.containsAll(selectedIdSet)) {
            throw new BizException("所选领导不在允许范围内");
        }
        if (selectedIdSet.size() != currentRuleStep.getAssigneeCount()) {
            throw new BizException("选择审批人数不正确");
        }

        return approverUserIds.stream()
                .map(this::requireUser)
                .collect(Collectors.toList());
    }

    private List<SelectedApproverResponse> resolveSelectableApprovers(LeaveRequest request,
                                                                      LeaveApproval pending,
                                                                      ApprovalRuleStep currentRuleStep) {
        List<UserAccount> candidates = switch (determineSelectionScenario(request)) {
            case SICK_WITHIN_MONTH -> findEnabledUsersByRoles(List.of(RoleCode.DEPUTY_STATIONMASTER));
            case SICK_OVER_MONTH, PERSONAL_10_TO_30 -> findEnabledUsersByRoles(List.of(RoleCode.STATIONMASTER));
            case PERSONAL_OVER_30 -> findEnabledUsersByRoles(List.of(RoleCode.STATIONMASTER, RoleCode.PARTY_SECRETARY));
            case NONE -> List.of();
        };

        return candidates.stream()
                .map(user -> SelectedApproverResponse.builder()
                        .stepNo(pending.getStepNo())
                        .stepName(currentRuleStep.getStepName())
                        .approverUserId(user.getId())
                        .approverName(user.getEmpName())
                        .approverRoleCode(user.getRoleCode())
                        .approverRoleName(user.getRoleName())
                        .candidateGroup(resolveCandidateGroupByRole(user.getRoleCode()))
                        .build())
                .collect(Collectors.toList());
    }

    private List<UserAccount> findEnabledUsersByRoles(List<String> roleCodes) {
        return userAccountMapper.findAll().stream()
                .filter(user -> user.getIsEnabled() != null && user.getIsEnabled() == 1)
                .filter(user -> roleCodes.contains(user.getRoleCode()))
                .sorted(Comparator.comparing(UserAccount::getId))
                .collect(Collectors.toList());
    }

    private String resolveCandidateGroupByRole(String roleCode) {
        return switch (roleCode) {
            case RoleCode.DEPUTY_STATIONMASTER -> CANDIDATE_GROUP_SUPERVISOR;
            case RoleCode.STATIONMASTER -> CANDIDATE_GROUP_STATIONMASTER;
            case RoleCode.UNIT_LEADER -> CANDIDATE_GROUP_UNIT_LEADER;
            case RoleCode.PARTY_SECRETARY -> CANDIDATE_GROUP_PARTY_AND_PRINCIPAL;
            default -> null;
        };
    }

    private SelectionScenario determineSelectionScenario(LeaveRequest request) {
        LeaveType leaveType = requireLeaveType(request.getLeaveTypeId());
        String leaveScope = resolveLeaveScope(leaveType);
        BigDecimal leaveDays = request.getLeaveDays();
        if (leaveDays == null) {
            return SelectionScenario.NONE;
        }

        if (LEAVE_SCOPE_SICK.equals(leaveScope)) {
            if (leaveDays.compareTo(DAY_30) > 0) {
                return SelectionScenario.SICK_OVER_MONTH;
            }
            if (leaveDays.compareTo(DAY_7) > 0) {
                return SelectionScenario.SICK_WITHIN_MONTH;
            }
        }

        if (LEAVE_SCOPE_PERSONAL.equals(leaveScope)) {
            if (leaveDays.compareTo(DAY_30) > 0) {
                return SelectionScenario.PERSONAL_OVER_30;
            }
            if (leaveDays.compareTo(DAY_10) > 0) {
                return SelectionScenario.PERSONAL_10_TO_30;
            }
        }
        return SelectionScenario.NONE;
    }

    private void ensureCurrentActor(UserAccount operator, LeaveRequest request, LeaveApproval pending) {
        if (pending.getApproverUserId() != null) {
            if (!pending.getApproverUserId().equals(operator.getId())) {
                throw new BizException("当前账号无权处理该节点");
            }
            requireApprovalPermission(operator, request, pending);
            return;
        }
        if (!pending.getApproverRoleCode().equals(operator.getRoleCode())) {
            throw new BizException("当前账号无权处理该节点");
        }
        if (RoleCode.ORG_PRINCIPAL.equals(operator.getRoleCode()) && !request.getOrgUnitId().equals(operator.getOrgUnitId())) {
            throw new BizException("当前账号只能处理本单位节点");
        }
        requireApprovalPermission(operator, request, pending);
    }

    private void requireApprovalPermission(UserAccount operator, LeaveRequest request, LeaveApproval pending) {
        List<ApprovalPermission> permissions = approvalPermissionMapper.findEnabledByOrgAndRole(request.getOrgUnitId(), operator.getRoleCode());
        if (permissions.isEmpty()) {
            throw new BizException("当前单位未配置该角色的审批权限");
        }

        String leaveScope = resolveLeaveScope(requireLeaveType(request.getLeaveTypeId()));
        boolean matched = permissions.stream().anyMatch(permission -> matchesApprovalPermission(permission, request, leaveScope));
        if (!matched) {
            throw new BizException("当前账号没有该请假单的审批权限");
        }
    }

    private boolean matchesApprovalPermission(ApprovalPermission permission, LeaveRequest request, String leaveScope) {
        if (permission.getApplicantType() != null
                && !permission.getApplicantType().equals(resolveRuleApplicantType(request.getApplicantType()))) {
            return false;
        }
        if (permission.getPositionLevelCode() != null && !permission.getPositionLevelCode().equals(request.getPositionLevelCode())) {
            return false;
        }
        if (permission.getLeaveScope() != null
                && !LEAVE_SCOPE_ALL.equals(permission.getLeaveScope())
                && !permission.getLeaveScope().equals(leaveScope)) {
            return false;
        }
        if (RoleCode.HR_SECTION_CHIEF.equals(permission.getRoleCode()) && LEAVE_SCOPE_SICK.equals(leaveScope)) {
            return true;
        }
        if (request.getLeaveDays() != null) {
            if (permission.getMinDays() != null && request.getLeaveDays().compareTo(permission.getMinDays()) < 0) {
                return false;
            }
            if (permission.getMaxDays() != null && request.getLeaveDays().compareTo(permission.getMaxDays()) > 0) {
                return false;
            }
        }
        return true;
    }

    private String resolveRuleApplicantType(String applicantType) {
        if (APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType) || APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(applicantType)) {
            return APPLICANT_TYPE_CADRE;
        }
        return applicantType;
    }

    private void decideApproval(LeaveApproval approval, Long approverUserId, Boolean approved, String comment, String signatureUrl) {
        approval.setApproverUserId(approverUserId);
        approval.setApprovalStatus(Boolean.TRUE.equals(approved) ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        approval.setApprovalComment(comment);
        approval.setSignatureUrl(normalizeSignatureUrl(signatureUrl));
        approval.setApprovedAt(LocalDateTime.now());
        leaveApprovalMapper.updateDecision(approval);
    }

    private String normalizeSignatureUrl(String signatureUrl) {
        if (signatureUrl == null || signatureUrl.isBlank() || "undefined".equalsIgnoreCase(signatureUrl.trim())) {
            return null;
        }
        String normalized = signatureUrl.trim();
        if (normalized.startsWith(LEGACY_FILE_HOST + FILE_URL_PREFIX)) {
            return normalized.replace(LEGACY_FILE_HOST, "");
        }
        return normalized;
    }

    private LeaveApproval buildApproval(Long leaveRequestId, ApprovalRuleStep step, Long approverUserId) {
        LeaveApproval approval = new LeaveApproval();
        approval.setLeaveRequestId(leaveRequestId);
        approval.setRuleStepId(step.getId());
        approval.setStepNo(step.getStepNo());
        approval.setActionType(step.getActionType());
        approval.setStepName(step.getStepName());
        approval.setApproverRoleCode(step.getApproverRoleCode());
        approval.setApproverUserId(approverUserId);
        approval.setApprovalStatus(ApprovalStatus.PENDING);
        return approval;
    }

    private ApprovalRecordResponse toApprovalRecordResponse(LeaveApproval approval) {
        UserAccount approver = approval.getApproverUserId() == null ? null : userAccountMapper.findById(approval.getApproverUserId());
        return ApprovalRecordResponse.builder()
                .stepNo(approval.getStepNo())
                .actionType(approval.getActionType())
                .stepName(approval.getStepName())
                .candidateGroup(approver == null ? null : approver.getLeaderGroupCode())
                .approverRoleCode(approval.getApproverRoleCode())
                .approverRoleName(approver == null ? toRoleName(approval.getApproverRoleCode()) : approver.getRoleName())
                .approverUserId(approval.getApproverUserId())
                .approverName(approver == null ? null : approver.getEmpName())
                .approvalStatus(approval.getApprovalStatus())
                .approvalComment(approval.getApprovalComment())
                .signatureUrl(approval.getSignatureUrl())
                .approvedAt(approval.getApprovedAt())
                .build();
    }

    private String resolveOrCreatePdfUrl(LeaveRequest request,
                                         UserAccount applicant,
                                         LeaveType leaveType,
                                         List<ApprovalRecordResponse> approvals) {
        if (!LeaveRequestStatus.APPROVED.equals(request.getStatus()) || request.getFinalApprovedAt() == null) {
            return null;
        }
        String existingPdfUrl = leaveDocumentService.resolveExistingPdfUrl(request.getId());
        if (existingPdfUrl != null) {
            return existingPdfUrl;
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
                .approvals(approvals)
                .build();
        return leaveDocumentService.generatePdf(request.getId(), detail);
    }

    private SelectedApproverResponse toSelectedApproverResponse(LeaveApproval approval) {
        UserAccount approver = requireUser(approval.getApproverUserId());
        return SelectedApproverResponse.builder()
                .stepNo(approval.getStepNo())
                .stepName(approval.getStepName())
                .approverUserId(approver.getId())
                .approverName(approver.getEmpName())
                .approverRoleCode(approval.getApproverRoleCode())
                .approverRoleName(approver.getRoleName())
                .candidateGroup(approver.getLeaderGroupCode())
                .build();
    }

    private enum SelectionScenario {
        NONE,
        SICK_WITHIN_MONTH,
        SICK_OVER_MONTH,
        PERSONAL_10_TO_30,
        PERSONAL_OVER_30
    }

    private UserAccount requireCurrentUser() {
        CurrentUser currentUser = UserContext.get();
        if (currentUser == null || currentUser.getUserId() == null) {
            throw new BizException("未登录或 token 已失效");
        }
        return requireUser(currentUser.getUserId());
    }

    private UserAccount resolveApplicant(CreateLeaveRequestDto dto) {
        String applicantName = dto.getApplicantName() == null ? null : dto.getApplicantName().trim();
        if (applicantName != null && !applicantName.isBlank()) {
            List<UserAccount> matchedUsers = userAccountMapper.findEnabledByEmpName(applicantName);
            if (matchedUsers.size() == 1) {
                return matchedUsers.get(0);
            }
            if (matchedUsers.size() > 1) {
                throw new BizException("请假人姓名重复，请使用唯一姓名或调整人员档案: " + applicantName);
            }
        }
        return requireUser(dto.getApplicantId());
    }

    private UserAccount requireUser(Long userId) {
        UserAccount user = userAccountMapper.findById(userId);
        if (user == null) {
            throw new BizException("用户不存在: " + userId);
        }
        return user;
    }

    private LeaveType requireLeaveType(Long leaveTypeId) {
        LeaveType leaveType = leaveTypeMapper.findById(leaveTypeId);
        if (leaveType == null) {
            throw new BizException("假别不存在");
        }
        return leaveType;
    }

    private LeaveRequest requireLeaveRequest(Long leaveId) {
        LeaveRequest request = leaveRequestMapper.findById(leaveId);
        if (request == null) {
            throw new BizException("请假单不存在");
        }
        return request;
    }

    private LeaveApproval requirePendingApproval(Long leaveId) {
        LeaveApproval approval = leaveApprovalMapper.findFirstPending(leaveId);
        if (approval == null) {
            throw new BizException("当前请假单不存在待处理节点");
        }
        return approval;
    }

    private String toRoleName(String roleCode) {
        return switch (roleCode) {
            case RoleCode.ATTENDANCE_ADMIN -> "考勤管理员";
            case RoleCode.ORG_PRINCIPAL -> "科室车间负责人";
            case RoleCode.HR_SECTION_CHIEF -> "劳动人事科科长";
            case RoleCode.DEPUTY_STATIONMASTER -> "主管站长";
            case RoleCode.STATIONMASTER -> "站长";
            case RoleCode.PARTY_SECRETARY -> "党委书记";
            default -> roleCode;
        };
    }

    private String generateRequestNo() {
        return "LR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String buildCancelComment(CancelLeaveRequestDto dto) {
        String reason = dto == null ? null : dto.getReason();
        if (reason == null || reason.isBlank()) {
            return "考勤管理员撤销请假单";
        }
        return "考勤管理员撤销请假单: " + reason.trim();
    }

    private String saveSignatureFile(Long leaveId, Integer stepNo, String originalName, java.io.InputStream inputStream) throws IOException {
        Path directory = Paths.get(fileStoragePath, "approval-signatures");
        Files.createDirectories(directory);
        String extension = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.')) : ".png";
        String filename = "leave_" + leaveId + "_step_" + stepNo + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = directory.resolve(filename);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return "/files/approval-signatures/" + filename;
    }

    private record BatchSignaturePayload(byte[] bytes, String originalFilename) {
    }
}
