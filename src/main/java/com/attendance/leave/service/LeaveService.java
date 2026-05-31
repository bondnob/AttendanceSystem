package com.attendance.leave.service;

import com.attendance.admin.mapper.ApprovalPermissionMapper;
import com.attendance.admin.mapper.OrgUnitMapper;
import com.attendance.admin.model.ApprovalPermission;
import com.attendance.admin.model.OrgUnit;
import com.attendance.auth.security.CurrentUser;
import com.attendance.auth.security.UserContext;
import com.attendance.common.PageResponse;
import com.attendance.exception.BizException;
import com.attendance.leave.dto.ApprovalRecordResponse;
import com.attendance.leave.dto.ApprovalSignatureUploadResponse;
import com.attendance.leave.dto.ApproveLeaveWithSignatureDto;
import com.attendance.leave.dto.BatchApproveLeaveDto;
import com.attendance.leave.dto.BatchApproveLeaveResponse;
import com.attendance.leave.dto.BatchLeavePdfRequest;
import com.attendance.leave.dto.BatchLeavePdfResponse;
import com.attendance.leave.dto.CreateLeaveRequestDto;
import com.attendance.leave.dto.CancelLeaveRequestDto;
import com.attendance.leave.dto.HandwrittenSignatureDto;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.time.LocalDate;
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
    private static final String APPLICANT_TYPE_WORKSHOP_DIRECTOR = "WORKSHOP_DIRECTOR";
    private static final String POSITION_STAFF = "STAFF";
    private static final String POSITION_GENERAL_CADRE = "GENERAL_CADRE";
    private static final String POSITION_SECTION_LEVEL = "SECTION_LEVEL";
    private static final String POSITION_WORKSHOP_DIRECTOR = "WORKSHOP_DIRECTOR";
    private static final String LEAVE_SCOPE_ALL = "ALL";
    private static final String LEAVE_SCOPE_OTHER = "OTHER";
    private static final String LEAVE_SCOPE_SICK = "SICK";
    private static final String LEAVE_SCOPE_PERSONAL = "PERSONAL";
    private static final Set<String> HR_APPROVAL_EXEMPT_LEAVE_CODES = Set.of("年", "丧", "搬", "病", "事");
    private static final String ACTION_APPROVE = "APPROVE";
    private static final String ACTION_SELECT = "SELECT";
    private static final String APPROVER_SOURCE_APPLICANT_ORG = "APPLICANT_ORG";
    private static final String APPROVER_SOURCE_HR_ORG = "HR_ORG";
    private static final String APPROVER_SOURCE_SELECTED = "SELECTED";
    private static final String CANDIDATE_GROUP_SUPERVISOR = "SUPERVISOR_LEADER";
    private static final String CANDIDATE_GROUP_STATIONMASTER = "STATIONMASTER";
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
    private final OrgUnitMapper orgUnitMapper;
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

        String applicantType = normalizeApplicantType(dto.getApplicantType());
        BigDecimal allowedDays = leaveType.getDefaultDays() == null ? null : BigDecimal.valueOf(leaveType.getDefaultDays());
        int daysInMonth = dto.getStartTime().toLocalDate().lengthOfMonth();
        boolean exceedsOneMonth = dto.getLeaveDays() != null && dto.getLeaveDays().compareTo(BigDecimal.valueOf(daysInMonth)) > 0;
        ApprovalRule rule = resolveApprovalRule(applicantType, applicant.getPositionLevelCode(), leaveType, dto.getLeaveDays(), exceedsOneMonth);
        String applicantNameSnapshot = resolveApplicantNameSnapshot(applicantType, applicant, dto);
        validateLeaveRequestRules(applicantNameSnapshot, leaveType, dto);
        List<ApprovalRuleStep> steps = prepareCreationSteps(rule.getId(), operator, applicantType, leaveType);
        if (Boolean.TRUE.equals(dto.getPartySecretaryFirst())) {
            swapPartySecretaryBeforeStationmaster(steps);
        }

        LeaveRequest request = new LeaveRequest();
        request.setRequestNo(generateRequestNo());
        request.setApplicantId(applicant.getId());
        request.setOrgUnitId(applicant.getOrgUnitId());
        request.setLeaveTypeId(leaveType.getId());
        request.setApprovalRuleId(rule.getId());
        request.setApplicantNameSnapshot(applicantNameSnapshot);
        request.setApplicantType(applicantType);
        request.setPositionLevelCode(resolvePositionLevel(applicantType, applicant.getPositionLevelCode()));
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

        leaveRequestMapper.insert(request);

        String firstApproverRoleCode = null;
        List<LeaveApproval> newApprovals = new ArrayList<>();
        boolean hrInitiated = isHrOrgUnit(operator.getOrgUnitId());
        for (ApprovalRuleStep step : steps) {
            UserAccount approver = resolveInitialApprover(applicant.getOrgUnitId(), step);
            Long approverUserId = approver == null ? null : approver.getId();
            LeaveApproval approval = buildApproval(request.getId(), step, approverUserId);
            if (hrInitiated && APPROVER_SOURCE_APPLICANT_ORG.equals(step.getApproverSource())
                    && RoleCode.ORG_PRINCIPAL.equals(step.getApproverRoleCode())) {
                approval.setApproverRoleCode(RoleCode.HR_SECTION_CHIEF);
            }
            leaveApprovalMapper.insert(approval);
            newApprovals.add(approval);
            if (firstApproverRoleCode == null) {
                firstApproverRoleCode = approval.getApproverRoleCode();
            }
        }
        swapApprovalStepNoIfNeeded(request, newApprovals);
        request.setCurrentStep(newApprovals.get(0).getStepNo());

        request.setCurrentApproverId(firstApproverRoleCode);
        leaveRequestMapper.updateApprovalState(request);

        return getLeaveDetail(request.getId());
    }

    @Transactional
    public LeaveDetailResponse updateRejectedLeave(Long leaveId, CreateLeaveRequestDto dto) {
        UserAccount operator = requireCurrentUser();
        LeaveRequest request = requireLeaveRequest(leaveId);
        ensureEditableRejectedByAdmin(operator, request);

        UserAccount applicant = resolveApplicant(dto);
        LeaveType leaveType = requireLeaveType(dto.getLeaveTypeId());

        String applicantType = normalizeApplicantType(dto.getApplicantType());
        BigDecimal allowedDays = leaveType.getDefaultDays() == null ? null : BigDecimal.valueOf(leaveType.getDefaultDays());
        int daysInMonth = dto.getStartTime().toLocalDate().lengthOfMonth();
        boolean exceedsOneMonth = dto.getLeaveDays() != null && dto.getLeaveDays().compareTo(BigDecimal.valueOf(daysInMonth)) > 0;
        ApprovalRule rule = resolveApprovalRule(applicantType, applicant.getPositionLevelCode(), leaveType, dto.getLeaveDays(), exceedsOneMonth);
        String applicantNameSnapshot = resolveApplicantNameSnapshot(applicantType, applicant, dto);
        validateLeaveRequestRules(applicantNameSnapshot, leaveType, dto);
        List<ApprovalRuleStep> steps = prepareCreationSteps(rule.getId(), operator, applicantType, leaveType);
        if (Boolean.TRUE.equals(dto.getPartySecretaryFirst())) {
            swapPartySecretaryBeforeStationmaster(steps);
        }

        request.setRequestNo(generateRequestNo());
        request.setApplicantId(applicant.getId());
        request.setOrgUnitId(applicant.getOrgUnitId());
        request.setLeaveTypeId(leaveType.getId());
        request.setApprovalRuleId(rule.getId());
        request.setApplicantNameSnapshot(applicantNameSnapshot);
        request.setApplicantType(applicantType);
        request.setPositionLevelCode(resolvePositionLevel(applicantType, applicant.getPositionLevelCode()));
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

        leaveRequestMapper.updateEditableRejected(request);

        leaveApprovalMapper.deleteByLeaveRequestId(leaveId);
        String firstApproverRoleCode = null;
        List<LeaveApproval> newApprovals = new ArrayList<>();
        boolean hrInitiated = isHrOrgUnit(operator.getOrgUnitId());
        for (ApprovalRuleStep step : steps) {
            UserAccount approver = resolveInitialApprover(applicant.getOrgUnitId(), step);
            Long approverUserId = approver == null ? null : approver.getId();
            LeaveApproval approval = buildApproval(request.getId(), step, approverUserId);
            if (hrInitiated && APPROVER_SOURCE_APPLICANT_ORG.equals(step.getApproverSource())
                    && RoleCode.ORG_PRINCIPAL.equals(step.getApproverRoleCode())) {
                approval.setApproverRoleCode(RoleCode.HR_SECTION_CHIEF);
            }
            leaveApprovalMapper.insert(approval);
            newApprovals.add(approval);
            if (firstApproverRoleCode == null) {
                firstApproverRoleCode = approval.getApproverRoleCode();
            }
        }
        swapApprovalStepNoIfNeeded(request, newApprovals);
        request.setCurrentStep(newApprovals.get(0).getStepNo());

        request.setCurrentApproverId(firstApproverRoleCode);
        leaveRequestMapper.updateApprovalState(request);

        return getLeaveDetail(leaveId);
    }

    @Transactional
    public void deleteRejectedLeave(Long leaveId) {
        UserAccount operator = requireCurrentUser();
        LeaveRequest request = requireLeaveRequest(leaveId);
        ensureEditableRejectedByAdmin(operator, request);
        leaveApprovalMapper.deleteByLeaveRequestId(leaveId);
        leaveRequestMapper.deleteById(leaveId);
    }

    @Transactional
    public LeaveDetailResponse approve(Long leaveId, ApproveLeaveWithSignatureDto dto) {
        UserAccount operator = requireCurrentUser();
        java.time.LocalDate effectiveSignatureDate = dto.getSignatureDate();
        if (effectiveSignatureDate == null && dto.getApprovedAt() != null) {
            effectiveSignatureDate = dto.getApprovedAt().toLocalDate();
        }
        return approveInternal(operator, leaveId, dto.getApproved(), dto.getComment(), dto.getSignatureFile(), dto.getSignatureUrl(), null, effectiveSignatureDate, dto.getApprovedAt());
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

        java.time.LocalDate effectiveSignatureDate = dto.getSignatureDate();
        if (effectiveSignatureDate == null && dto.getApprovedAt() != null) {
            effectiveSignatureDate = dto.getApprovedAt().toLocalDate();
        }

        List<LeaveDetailResponse> records = new ArrayList<>();
        for (Long leaveId : dto.getLeaveIds()) {
            records.add(approveInternal(operator, leaveId, dto.getApproved(), dto.getComment(),
                    null, dto.getSignatureUrl(), signatureBytes == null ? null : new BatchSignaturePayload(signatureBytes, originalFilename), effectiveSignatureDate, dto.getApprovedAt()));
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
        LeaveApproval pending = requireCurrentPendingApproval(request);
        if (!ACTION_APPROVE.equals(pending.getActionType())) {
            throw new BizException("当前节点不是审批节点，不能上传审批签名");
        }
        ensureCurrentActor(operator, request, pending);

        boolean signatureRequired = leaveSignRequirementService.isSignatureRequired(operator.getRoleCode(), request.getLeaveTypeId());
        if (!signatureRequired) {
            throw new BizException("当前审批节点不要求上传电子签名");
        }

        if (dto != null && dto.getSignatureFile() != null && !dto.getSignatureFile().isEmpty()) {
            throw new BizException("电子签名只能由超级管理员预先上传，审批时不允许临时上传");
        }

        String signatureUrl = normalizeSignatureUrl(operator.getSignatureUrl());
        if (signatureUrl == null || signatureUrl.isBlank()) {
            throw new BizException("当前账号未配置电子签名，无法审批");
        }
        return ApprovalSignatureUploadResponse.builder()
                .leaveId(leaveId)
                .stepNo(pending.getStepNo())
                .signatureUrl(signatureUrl)
                .build();
    }

    @Transactional
    public LeaveDetailResponse uploadHandwrittenSignature(Long leaveId, HandwrittenSignatureDto dto) {
        LeaveRequest request = requireLeaveRequest(leaveId);
        String type = dto.getApplicantType().trim().toUpperCase();
        if (!"APPLICANT".equals(type) && !"TEAM_LEADER".equals(type) && !"APPLICANT_DATE".equals(type)) {
            throw new BizException("签名类型只能是 APPLICANT、TEAM_LEADER 或 APPLICANT_DATE，分别为请假人姓名、班组长姓名、请假人日期");
        }

        if (dto.getSignatureFile() == null || dto.getSignatureFile().isEmpty()) {
            throw new BizException("签名文件不能为空");
        }
        try {
            String url = saveHandwrittenSignatureFile(leaveId, type, dto.getSignatureFile().getOriginalFilename(),
                    dto.getSignatureFile().getInputStream());
            if ("APPLICANT".equals(type)) {
                leaveRequestMapper.updateApplicantSignatureUrl(leaveId, url);
                request.setApplicantSignatureUrl(url);
            } else if ("APPLICANT_DATE".equals(type)) {
                leaveRequestMapper.updateApplicantDateSignatureUrl(leaveId, url);
                request.setApplicantDateSignatureUrl(url);
            } else {
                leaveRequestMapper.updateTeamLeaderSignatureUrlOnly(leaveId, url);
                request.setTeamLeaderSignatureUrl(url);
            }
            return getLeaveDetail(leaveId);
        } catch (IOException ex) {
            throw new BizException("手写签名上传失败");
        }
    }

    @Transactional
    public LeaveDetailResponse uploadTeamLeaderSignatureDate(Long leaveId, LocalDate signatureDate) {
        LeaveRequest request = requireLeaveRequest(leaveId);
        leaveRequestMapper.updateTeamLeaderSignatureDate(leaveId, signatureDate);
        request.setTeamLeaderSignatureDate(signatureDate);
        return getLeaveDetail(leaveId);
    }

    private String saveHandwrittenSignatureFile(Long leaveId, String type, String originalName, java.io.InputStream inputStream) throws IOException {
        Path directory = Paths.get(fileStoragePath, "handwritten-signatures");
        Files.createDirectories(directory);
        String extension = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.')) : ".png";
        String filename = "leave_" + leaveId + "_" + type.toLowerCase() + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = directory.resolve(filename);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return "/files/handwritten-signatures/" + filename;
    }

    @Transactional
    public LeaveDetailResponse updateSubmittedAt(Long leaveId, LocalDateTime submittedAt) {
        UserAccount operator = requireCurrentUser();
        if (!"SYSTEM_ADMIN".equals(operator.getRoleCode())) {
            throw new BizException("只有超级管理员可以修改申请时间");
        }
        LeaveRequest request = requireLeaveRequest(leaveId);
        leaveRequestMapper.updateSubmittedAt(leaveId, submittedAt);
        request.setSubmittedAt(submittedAt);
        return getLeaveDetail(leaveId);
    }

    @Transactional
    public LeaveDetailResponse updateSignatureDate(Long leaveId, Integer stepNo, java.time.LocalDate signatureDate) {
        UserAccount operator = requireCurrentUser();
        if (!"SYSTEM_ADMIN".equals(operator.getRoleCode())) {
            throw new BizException("只有超级管理员可以修改签字日期");
        }
        requireLeaveRequest(leaveId);
        int rows = leaveApprovalMapper.updateSignatureDate(leaveId, stepNo, signatureDate);
        if (rows == 0) {
            throw new BizException("未找到对应的审批节点");
        }
        return getLeaveDetail(leaveId);
    }

    @Transactional
    public LeaveDetailResponse selectApprovers(Long leaveId, SelectApproversDto dto) {
        UserAccount operator = requireCurrentUser();
        LeaveRequest request = requireLeaveRequest(leaveId);
        ensureNotCancelled(request);
        LeaveApproval pending = requireCurrentPendingApproval(request);
        if (!ACTION_SELECT.equals(pending.getActionType())) {
            throw new BizException("当前节点不是选择审批人节点");
        }
        ensureCurrentActor(operator, request, pending);

        ApprovalRuleStep currentRuleStep = requireRuleStep(request.getApprovalRuleId(), pending.getRuleStepId());
        if (dto.getApproverUserIds().size() != currentRuleStep.getAssigneeCount()) {
            throw new BizException("选择审批人数不正确");
        }

        List<UserAccount> selectedUsers = validateAndResolveSelectedApprovers(request, pending, currentRuleStep, dto.getApproverUserIds());

        decideApproval(pending, operator.getId(), true, dto.getComment(), null, null, null);

        List<LeaveApproval> targets = resolveOrCreateSelectedApprovalTargets(request, currentRuleStep, selectedUsers);

        java.util.Map<String, UserAccount> userByRole = selectedUsers.stream()
                .collect(Collectors.toMap(UserAccount::getRoleCode, u -> u, (left, right) -> left));
        for (LeaveApproval target : targets) {
            UserAccount user = userByRole.get(target.getApproverRoleCode());
            if (user != null) {
                target.setApproverUserId(user.getId());
                leaveApprovalMapper.updateApprover(target);
            }
        }
        swapApprovalStepNoIfNeeded(request, targets);

        moveToNextStep(request);
        return getLeaveDetail(leaveId);
    }

    @Transactional
    public LeaveDetailResponse reSelectApprovers(Long leaveId, SelectApproversDto dto) {
        UserAccount operator = requireCurrentUser();
        LeaveRequest request = requireLeaveRequest(leaveId);
        ensureNotCancelled(request);
        if (!LeaveRequestStatus.APPROVING.equals(request.getStatus())) {
            throw new BizException("当前请假单状态不允许重选领导");
        }

        List<LeaveApproval> allApprovals = leaveApprovalMapper.findByLeaveRequestId(leaveId);

        LeaveApproval selectApproval = allApprovals.stream()
                .filter(a -> ACTION_SELECT.equals(a.getActionType())
                        && ApprovalStatus.APPROVED.equals(a.getApprovalStatus())
                        && a.getApproverUserId() != null
                        && a.getApproverUserId().equals(operator.getId()))
                .max(java.util.Comparator.comparing(LeaveApproval::getStepNo))
                .orElseThrow(() -> new BizException("未找到已审批的选择领导节点，不能重选"));

        List<LeaveApproval> pendingApproveAfterSelect = allApprovals.stream()
                .filter(a -> a.getStepNo() > selectApproval.getStepNo()
                        && ApprovalStatus.PENDING.equals(a.getApprovalStatus())
                        && ACTION_APPROVE.equals(a.getActionType()))
                .collect(Collectors.toList());

        if (pendingApproveAfterSelect.isEmpty()) {
            throw new BizException("后续领导已审批，不能重选");
        }

        ApprovalRuleStep selectRuleStep = requireRuleStep(request.getApprovalRuleId(), selectApproval.getRuleStepId());
        if (dto.getApproverUserIds().size() != selectRuleStep.getAssigneeCount()) {
            throw new BizException("选择审批人数不正确");
        }

        LeaveApproval virtualPending = new LeaveApproval();
        virtualPending.setStepNo(selectApproval.getStepNo());
        virtualPending.setRuleStepId(selectApproval.getRuleStepId());
        virtualPending.setActionType(ACTION_SELECT);
        List<UserAccount> selectedUsers = validateAndResolveSelectedApprovers(request, virtualPending, selectRuleStep, dto.getApproverUserIds());

        leaveApprovalMapper.deletePendingAfterStep(leaveId, selectApproval.getStepNo());

        selectApproval.setApprovalStatus(ApprovalStatus.PENDING);
        selectApproval.setApproverUserId(operator.getId());
        selectApproval.setApprovalComment(dto.getComment());
        selectApproval.setSignatureUrl(null);
        selectApproval.setApprovedAt(null);
        leaveApprovalMapper.updateDecision(selectApproval);

        List<LeaveApproval> newTargets = resolveOrCreateSelectedApprovalTargets(request, selectRuleStep, selectedUsers);
        java.util.Map<String, UserAccount> userByRole = selectedUsers.stream()
                .collect(Collectors.toMap(UserAccount::getRoleCode, u -> u, (left, right) -> left));
        for (LeaveApproval target : newTargets) {
            UserAccount user = userByRole.get(target.getApproverRoleCode());
            if (user != null) {
                target.setApproverUserId(user.getId());
                leaveApprovalMapper.updateApprover(target);
            }
        }
        swapApprovalStepNoIfNeeded(request, newTargets);

        request.setStatus(LeaveRequestStatus.APPROVING);
        request.setCurrentStep(selectApproval.getStepNo());
        request.setCurrentActionType(ACTION_SELECT);
        request.setCurrentApproverId(operator.getRoleCode());
        request.setFinalApprovedAt(null);
        leaveRequestMapper.updateApprovalState(request);

        return getLeaveDetail(leaveId);
    }

    private List<LeaveApproval> resolveOrCreateSelectedApprovalTargets(LeaveRequest request,
                                                                       ApprovalRuleStep currentRuleStep,
                                                                       List<UserAccount> selectedUsers) {
        int targetCount = selectedUsers.size();
        SelectionScenario scenario = determineSelectionScenario(request);
        List<ApprovalRuleStep> selectedSteps = requireRuleSteps(request.getApprovalRuleId()).stream()
                .filter(step -> step.getStepNo() > currentRuleStep.getStepNo())
                .filter(step -> ACTION_APPROVE.equals(step.getActionType()))
                .filter(step -> APPROVER_SOURCE_SELECTED.equals(step.getApproverSource()))
                .filter(step -> SelectionScenario.SICK_OVER_MONTH.equals(scenario)
                        || currentRuleStep.getCandidateGroup() == null
                        || currentRuleStep.getCandidateGroup().equals(step.getCandidateGroup()))
                .collect(Collectors.toList());
        if (SelectionScenario.SECTION_LEVEL.equals(scenario)) {
            selectedSteps = sortSectionLevelSelectedSteps(selectedSteps, selectedUsers, Integer.valueOf(1).equals(request.getPartySecretaryFirst()));
        } else if (SelectionScenario.SICK_OVER_MONTH.equals(scenario)) {
            selectedSteps = sortSectionLevelSelectedSteps(selectedSteps, selectedUsers, false);
        }
        if (selectedSteps.size() < targetCount) {
            throw new BizException("后续领导审批节点配置不完整");
        }
        selectedSteps = selectedSteps.stream()
                .limit(targetCount)
                .collect(Collectors.toList());

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

    private void swapApprovalStepNoIfNeeded(LeaveRequest request, List<LeaveApproval> approvals) {
        if (!Integer.valueOf(1).equals(request.getPartySecretaryFirst())) {
            return;
        }
        LeaveApproval sm = null;
        LeaveApproval ps = null;
        for (LeaveApproval a : approvals) {
            if (RoleCode.STATIONMASTER.equals(a.getApproverRoleCode())) {
                sm = a;
            } else if (RoleCode.PARTY_SECRETARY.equals(a.getApproverRoleCode())) {
                ps = a;
            }
        }
        if (sm != null && ps != null && sm.getStepNo() < ps.getStepNo()) {
            Integer smStepNo = sm.getStepNo();
            sm.setStepNo(ps.getStepNo());
            ps.setStepNo(smStepNo);
            leaveApprovalMapper.updateStepNo(sm.getId(), sm.getStepNo());
            leaveApprovalMapper.updateStepNo(ps.getId(), ps.getStepNo());
        }
    }

    private List<ApprovalRuleStep> sortSectionLevelSelectedSteps(List<ApprovalRuleStep> selectedSteps,
                                                                 List<UserAccount> selectedUsers,
                                                                 boolean partySecretaryFirst) {
        java.util.Map<String, ApprovalRuleStep> stepByRoleCode = selectedSteps.stream()
                .collect(Collectors.toMap(ApprovalRuleStep::getApproverRoleCode, step -> step, (left, right) -> left));
        List<ApprovalRuleStep> orderedSteps = new ArrayList<>();
        for (UserAccount user : selectedUsers) {
            ApprovalRuleStep step = stepByRoleCode.get(user.getRoleCode());
            if (step == null) {
                throw new BizException("所选领导与审批节点配置不匹配");
            }
            orderedSteps.add(step);
        }
        if (partySecretaryFirst) {
            int stationmasterIdx = -1;
            int partySecretaryIdx = -1;
            for (int i = 0; i < orderedSteps.size(); i++) {
                if (RoleCode.STATIONMASTER.equals(orderedSteps.get(i).getApproverRoleCode())) {
                    stationmasterIdx = i;
                } else if (RoleCode.PARTY_SECRETARY.equals(orderedSteps.get(i).getApproverRoleCode())) {
                    partySecretaryIdx = i;
                }
            }
            if (stationmasterIdx >= 0 && partySecretaryIdx >= 0 && partySecretaryIdx > stationmasterIdx) {
                ApprovalRuleStep temp = orderedSteps.get(stationmasterIdx);
                orderedSteps.set(stationmasterIdx, orderedSteps.get(partySecretaryIdx));
                orderedSteps.set(partySecretaryIdx, temp);
            }
        }
        return orderedSteps;
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
        request.setCurrentApproverId(null);
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
                .applicantSignatureUrl(request.getApplicantSignatureUrl())
                .applicantDateSignatureUrl(request.getApplicantDateSignatureUrl())
                .teamLeaderSignatureUrl(request.getTeamLeaderSignatureUrl())
                .teamLeaderSignatureDate(request.getTeamLeaderSignatureDate())
                .partySecretaryFirst(request.getPartySecretaryFirst())
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

    public BatchLeavePdfResponse batchDownloadPdf(BatchLeavePdfRequest dto) {
        UserAccount operator = requireCurrentUser();
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
            Path pdfPath = leaveDocumentService.resolveStoredFilePath(detail.getPdfUrl());
            if (pdfPath == null) {
                throw new BizException("请假单 PDF 文件不存在: " + request.getRequestNo());
            }
            pdfPaths.add(pdfPath);
        }

        String pdfUrl = leaveDocumentService.generateMergedPdf(pdfPaths);
        return BatchLeavePdfResponse.builder()
                .pdfUrl(pdfUrl)
                .recordCount(requests.size())
                .build();
    }

    public List<SelectedApproverResponse> getSelectedApprovers(Long leaveId) {
        LeaveRequest request = requireLeaveRequest(leaveId);
        LeaveApproval pending = requireCurrentPendingApproval(request);
        if (!LeaveRequestStatus.PENDING.equals(request.getStatus())
                && !LeaveRequestStatus.APPROVING.equals(request.getStatus())) {
            throw new BizException("当前请假单状态不允许审批");
        }
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
        LocalDateTime monthStart = currentMonthStart();
        LocalDateTime monthEnd = monthStart.plusMonths(1);

        Long total;
        List<LeaveListItemResponse> records;
        if (shouldUsePendingApproverView(operator)) {
            total = leaveRequestMapper.countByResponsibleApprover(
                    operator.getId(), operator.getRoleCode(), operator.getOrgUnitId(), normalizedStatus, leaveTypeId, monthStart, monthEnd);
            List<LeaveRequest> requests = leaveRequestMapper.findPageByResponsibleApprover(
                    operator.getId(), operator.getRoleCode(), operator.getOrgUnitId(), normalizedStatus, leaveTypeId,
                    monthStart, monthEnd, offset, safePageSize);
            records = toLeaveListItemResponses(requests);
        } else {
            total = leaveRequestMapper.countByScope(orgUnitId, applicantId, normalizedStatus, leaveTypeId, monthStart, monthEnd);
            List<LeaveRequest> requests = leaveRequestMapper.findPageByScope(
                    orgUnitId, applicantId, normalizedStatus, leaveTypeId, monthStart, monthEnd, offset, safePageSize);
            records = toLeaveListItemResponses(requests);
        }
        return PageResponse.<LeaveListItemResponse>builder()
                .total(total == null ? 0L : total)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .records(records)
                .build();
    }

    public PageResponse<LeaveListItemResponse> listRecentThreeMonthApprovalLeaves(String status,
                                                                                  Long leaveTypeId,
                                                                                  Integer pageNum,
                                                                                  Integer pageSize) {
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
        LocalDateTime monthEnd = currentMonthStart();
        LocalDateTime monthStart = currentMonthStart().minusMonths(3);

        Long total = leaveRequestMapper.countByScope(
                orgUnitId, applicantId, normalizedStatus, leaveTypeId, monthStart, monthEnd);
        List<LeaveRequest> requests = leaveRequestMapper.findPageByScope(
                orgUnitId, applicantId, normalizedStatus, leaveTypeId, monthStart, monthEnd, offset, safePageSize);
        List<LeaveListItemResponse> records = toLeaveListItemResponses(requests);
        return PageResponse.<LeaveListItemResponse>builder()
                .total(total == null ? 0L : total)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .records(records)
                .build();
    }

    private List<LeaveListItemResponse> toLeaveListItemResponses(List<LeaveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<Long> leaveRequestIds = requests.stream()
                .map(LeaveRequest::getId)
                .toList();
        List<LeaveApproval> allApprovals = leaveApprovalMapper.findByLeaveRequestIds(leaveRequestIds);
        java.util.Map<Long, List<String>> approvedRolesByLeaveId = buildApprovedRolesByLeaveId(allApprovals);
        java.util.Map<Long, List<ApprovalRecordResponse>> approvalsByLeaveId = buildApprovalsByLeaveId(allApprovals);
        return requests.stream()
                .map(request -> toLeaveListItemResponse(
                        request,
                        approvedRolesByLeaveId.getOrDefault(request.getId(), List.of()),
                        approvalsByLeaveId.getOrDefault(request.getId(), List.of())))
                .collect(Collectors.toList());
    }

    public PendingSummaryResponse getPendingSummary() {
        UserAccount operator = requireCurrentUser();
        Long count = leaveApprovalMapper.countPendingForUser(operator.getId(), operator.getRoleCode(), operator.getOrgUnitId());
        return PendingSummaryResponse.builder()
                .pendingCount(count == null ? 0L : count)
                .build();
    }

    public PageResponse<LeaveListItemResponse> listAllLeaves(String status, Long leaveTypeId, String applicantName, Integer pageNum, Integer pageSize) {
        UserAccount operator = requireCurrentUser();
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
        Long total = leaveRequestMapper.countAll(status, leaveTypeId, keyword, threeMonthsAgo);
        List<LeaveRequest> requests = leaveRequestMapper.findAllPage(status, leaveTypeId, keyword, threeMonthsAgo, offset, safePageSize);
        List<LeaveListItemResponse> records = toLeaveListItemResponses(requests);
        return PageResponse.<LeaveListItemResponse>builder()
                .total(total == null ? 0L : total)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .records(records)
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

    private LeaveListItemResponse toLeaveListItemResponse(LeaveRequest request, List<String> approvedRoles,
                                                           List<ApprovalRecordResponse> approvals) {
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
                .jobTitleSnapshot(request.getJobTitleSnapshot())
                .teamLeaderSnapshot(request.getTeamLeaderSnapshot())
                .reason(request.getReason())
                .remark(request.getRemark())
                .submittedAt(request.getSubmittedAt())
                .approvedRoles(approvedRoles)
                .currentApproverId(request.getCurrentApproverId())
                .applicantSignatureUrl(request.getApplicantSignatureUrl())
                .teamLeaderSignatureUrl(request.getTeamLeaderSignatureUrl())
                .partySecretaryFirst(request.getPartySecretaryFirst())
                .approvals(approvals)
                .build();
    }

    private java.util.Map<Long, List<ApprovalRecordResponse>> buildApprovalsByLeaveId(List<LeaveApproval> approvals) {
        if (approvals == null || approvals.isEmpty()) {
            return Collections.emptyMap();
        }
        java.util.Map<Long, List<ApprovalRecordResponse>> result = new java.util.HashMap<>();
        for (LeaveApproval approval : approvals) {
            result.computeIfAbsent(approval.getLeaveRequestId(), key -> new ArrayList<>())
                    .add(toApprovalRecordResponse(approval));
        }
        return result;
    }

    private java.util.Map<Long, List<String>> buildApprovedRolesByLeaveId(List<LeaveApproval> approvals) {
        if (approvals == null || approvals.isEmpty()) {
            return Collections.emptyMap();
        }
        java.util.Map<Long, LinkedHashMap<String, Boolean>> roleSetByLeaveId = new java.util.HashMap<>();
        for (LeaveApproval approval : approvals) {
            if (!ApprovalStatus.APPROVED.equals(approval.getApprovalStatus())) {
                continue;
            }
            if (!ACTION_APPROVE.equals(approval.getActionType())) {
                continue;
            }
            if (approval.getApproverRoleCode() == null || approval.getApproverRoleCode().isBlank()) {
                continue;
            }
            roleSetByLeaveId
                    .computeIfAbsent(approval.getLeaveRequestId(), key -> new LinkedHashMap<>())
                    .putIfAbsent(approval.getApproverRoleCode(), Boolean.TRUE);
        }

        java.util.Map<Long, List<String>> approvedRolesByLeaveId = new java.util.HashMap<>();
        roleSetByLeaveId.forEach((leaveId, roleSet) ->
                approvedRolesByLeaveId.put(leaveId, new ArrayList<>(roleSet.keySet())));
        return approvedRolesByLeaveId;
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
        List<LeaveRequest> requests = leaveRequestMapper.findApprovedByDateRange(operator.getOrgUnitId(), startDate, endDate);
        if (requests.isEmpty()) {
            throw new BizException("该时间段内没有已审批完成的请假记录单");
        }
        return requests;
    }

    private void ensureEditableRejectedByAdmin(UserAccount operator, LeaveRequest request) {
        if (!RoleCode.ATTENDANCE_ADMIN.equals(operator.getRoleCode())) {
            throw new BizException("只有考勤管理员可以操作驳回请假单");
        }
        if (!LeaveRequestStatus.REJECTED.equals(request.getStatus()) && !LeaveRequestStatus.PENDING.equals(request.getStatus())) {
            throw new BizException("当前请假单不是已驳回或待审批状态");
        }
        if (!operator.getId().equals(request.getSubmittedBy())) {
            throw new BizException("只能操作本人发起的驳回请假单");
        }
        if (!operator.getOrgUnitId().equals(request.getOrgUnitId())) {
            throw new BizException("只能操作本单位的驳回请假单");
        }
    }

    private void ensureNotCancelled(LeaveRequest request) {
        if (LeaveRequestStatus.CANCELLED.equals(request.getStatus())) {
            throw new BizException("请假单已取消，不能继续处理");
        }
    }

    private LeaveDetailResponse approveInternal(UserAccount operator, Long leaveId, Boolean approved, String comment,
                                                org.springframework.web.multipart.MultipartFile signatureFile,
                                                String signatureUrl, BatchSignaturePayload batchSignaturePayload,
                                                java.time.LocalDate signatureDate, LocalDateTime approvedAt) {
        LeaveRequest request = requireLeaveRequest(leaveId);
        ensureNotCancelled(request);
        LeaveApproval pending = requireCurrentPendingApproval(request);
        if (!LeaveRequestStatus.PENDING.equals(request.getStatus())
                && !LeaveRequestStatus.APPROVING.equals(request.getStatus())) {
            throw new BizException("当前请假单状态不允许审批");
        }
        if (!ACTION_APPROVE.equals(pending.getActionType())) {
            throw new BizException("当前节点不是审批节点");
        }
        ensureCurrentActor(operator, request, pending);

        String finalSignatureUrl = normalizeSignatureUrl(operator.getSignatureUrl());
        boolean signatureRequired = leaveSignRequirementService.isSignatureRequired(operator.getRoleCode(), request.getLeaveTypeId());
        ensureNoTemporarySignatureOverride(signatureFile, signatureUrl, batchSignaturePayload, finalSignatureUrl);
        if (signatureRequired) {
            if (finalSignatureUrl == null || finalSignatureUrl.isBlank()) {
                throw new BizException("当前账号未配置电子签名，无法审批");
            }
        }

        decideApproval(pending, operator.getId(), approved, comment, finalSignatureUrl, signatureDate, approvedAt);

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
                && !RoleCode.WORKSHOP_PARTY_SECRETARY.equals(operator.getRoleCode())
                && !"NONE".equals(operator.getApprovalScope());
    }

    private LocalDateTime currentMonthStart() {
        return LocalDate.now().withDayOfMonth(1).atStartOfDay();
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

    private void moveToNextStep(LeaveRequest request) {
        LeaveApproval nextPending = leaveApprovalMapper.findFirstPending(request.getId());
        if (nextPending == null) {
            request.setStatus(LeaveRequestStatus.APPROVED);
            request.setCurrentStep(ApprovalStep.FINISHED);
            request.setCurrentActionType(null);
            request.setCurrentApproverId(null);
            request.setFinalApprovedAt(LocalDateTime.now());
        } else {
            request.setStatus(LeaveRequestStatus.APPROVING);
            request.setCurrentStep(nextPending.getStepNo());
            request.setCurrentActionType(nextPending.getActionType());
            request.setCurrentApproverId(nextPending.getApproverRoleCode());
        }
        leaveRequestMapper.updateApprovalState(request);
    }

    private ApprovalRule resolveApprovalRule(String applicantType, String actualPositionLevel, LeaveType leaveType, BigDecimal leaveDays, boolean exceedsOneMonth) {
        String leaveScope = resolveLeaveScope(leaveType);
        String positionLevel = resolveRulePositionLevel(applicantType, actualPositionLevel, leaveScope);
        String ruleApplicantType = resolveRuleApplicantType(applicantType, leaveScope);

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
        if (APPLICANT_TYPE_WORKSHOP_DIRECTOR.equals(applicantType)) {
            return POSITION_WORKSHOP_DIRECTOR;
        }
        if (APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType)) {
            return POSITION_GENERAL_CADRE;
        }
        if (APPLICANT_TYPE_CADRE.equals(applicantType) && POSITION_SECTION_LEVEL.equals(actualPositionLevel)) {
            return POSITION_SECTION_LEVEL;
        }
        return APPLICANT_TYPE_CADRE.equals(applicantType) ? POSITION_GENERAL_CADRE : POSITION_STAFF;
    }

    private String normalizeApplicantType(String applicantType) {
        if (applicantType == null || applicantType.isBlank()) {
            throw new BizException("人员类别不能为空");
        }
        if (APPLICANT_TYPE_EMPLOYEE.equalsIgnoreCase(applicantType)) {
            return APPLICANT_TYPE_EMPLOYEE;
        }
        if (APPLICANT_TYPE_CADRE.equalsIgnoreCase(applicantType)) {
            return APPLICANT_TYPE_GENERAL_CADRE;
        }
        if (APPLICANT_TYPE_GENERAL_CADRE.equalsIgnoreCase(applicantType)) {
            return APPLICANT_TYPE_GENERAL_CADRE;
        }
        if (APPLICANT_TYPE_SECTION_LEVEL_CADRE.equalsIgnoreCase(applicantType)
                || POSITION_SECTION_LEVEL.equalsIgnoreCase(applicantType)) {
            return APPLICANT_TYPE_SECTION_LEVEL_CADRE;
        }
        if (APPLICANT_TYPE_WORKSHOP_DIRECTOR.equalsIgnoreCase(applicantType)
                || POSITION_WORKSHOP_DIRECTOR.equalsIgnoreCase(applicantType)) {
            return APPLICANT_TYPE_WORKSHOP_DIRECTOR;
        }
        throw new BizException("人员类别只能是 职工、一般干部、中层正职、车间主任");
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
        if (rule.getExceedsMonthOnly() == null) {
            return true;
        }
        return exceedsOneMonth == (rule.getExceedsMonthOnly() == 1);
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

    private void validateLeaveRequestRules(String applicantNameSnapshot, LeaveType leaveType, CreateLeaveRequestDto dto) {
        if (dto.getStartTime().isAfter(dto.getEndTime())) {
            throw new BizException("结束时间不能早于开始时间");
        }
        if (!LEAVE_SCOPE_PERSONAL.equals(resolveLeaveScope(leaveType))) {
            return;
        }
        validatePersonalLeaveRules(applicantNameSnapshot, dto);
    }

    private void validatePersonalLeaveRules(String applicantNameSnapshot, CreateLeaveRequestDto dto) {
        BigDecimal leaveDays = dto.getLeaveDays();
        if (leaveDays == null) {
            return;
        }

        java.time.LocalDate startDate = dto.getStartTime().toLocalDate();
        int daysInMonth = startDate.lengthOfMonth();
        BigDecimal monthThreshold = BigDecimal.valueOf(daysInMonth);

        if (leaveDays.compareTo(monthThreshold) >= 0 && leaveDays.compareTo(DAY_60) > 0) {
            throw new BizException("特殊情况单次事假原则上不得超过2个月");
        }

        java.time.LocalDate endDate = dto.getEndTime().toLocalDate();
        java.time.LocalDate monthStart = startDate.withDayOfMonth(1);
        java.time.LocalDate monthEnd = startDate.withDayOfMonth(daysInMonth);
        java.time.LocalDate quarterStart = startDate.withMonth(firstMonthOfQuarter(startDate.getMonth()).getValue()).withDayOfMonth(1);
        java.time.LocalDate quarterEnd = quarterStart.plusMonths(2).withDayOfMonth(quarterStart.plusMonths(2).lengthOfMonth());
        java.time.LocalDate yearStart = startDate.withDayOfYear(1);
        java.time.LocalDate yearEnd = startDate.withDayOfYear(startDate.lengthOfYear());

        if (leaveDays.compareTo(DAY_1) <= 0) {
            long monthlyCount = countPersonalLeave(applicantNameSnapshot, monthStart, monthEnd, null, DAY_1);
            if (monthlyCount >= 3) {
                throw new BizException("单次请事假1天以内的每月不得超过3次");
            }
            return;
        }

        if (leaveDays.compareTo(DAY_2) >= 0 && leaveDays.compareTo(DAY_5) < 0) {
            long monthlyCount = countPersonalLeave(applicantNameSnapshot, monthStart, monthEnd, DAY_2, BigDecimal.valueOf(4.99));
            if (monthlyCount >= 2) {
                throw new BizException("单次请事假2天及以上至5天以内的每月不得超过2次");
            }
            long quarterCount = countPersonalLeave(applicantNameSnapshot, quarterStart, quarterEnd, DAY_2, BigDecimal.valueOf(4.99));
            if (quarterCount >= 3) {
                throw new BizException("单次请事假2天及以上至5天以内的季度内不得超过3次");
            }
            ensureNoContinuousPersonalLeave(applicantNameSnapshot, startDate, endDate);
            return;
        }

        if (leaveDays.compareTo(DAY_5) >= 0 && leaveDays.compareTo(DAY_10) < 0) {
            long yearlyCount = countPersonalLeave(applicantNameSnapshot, yearStart, yearEnd, DAY_5, BigDecimal.valueOf(9.99));
            if (yearlyCount >= 3) {
                throw new BizException("单次请事假5天及以上至10天以内的年度内不得超过3次");
            }
            return;
        }

        if (leaveDays.compareTo(DAY_10) >= 0 && leaveDays.compareTo(monthThreshold) < 0) {
            long yearlyCount = countPersonalLeave(applicantNameSnapshot, yearStart, yearEnd, DAY_10, monthThreshold.subtract(BigDecimal.ONE));
            if (yearlyCount >= 3) {
                throw new BizException("单次请事假10天及以上至" + daysInMonth + "天以内的年度内不得超过3次");
            }
            return;
        }

        if (leaveDays.compareTo(monthThreshold) >= 0) {
            long yearlyCount = countPersonalLeave(applicantNameSnapshot, yearStart, yearEnd, monthThreshold, null);
            if (yearlyCount >= 2) {
                throw new BizException("单次请事假" + daysInMonth + "天及以上的年度内不得超过2次");
            }
        }
    }

    private long countPersonalLeave(String applicantNameSnapshot, java.time.LocalDate periodStart, java.time.LocalDate periodEnd,
                                    BigDecimal minDays, BigDecimal maxDays) {
        Long count = leaveRequestMapper.countLeaveRequestsByApplicantAndRange(
                applicantNameSnapshot,
                requirePersonalLeaveTypeId(),
                EFFECTIVE_LEAVE_STATUSES,
                periodStart,
                periodEnd,
                minDays,
                maxDays
        );
        return count == null ? 0L : count;
    }

    private void ensureNoContinuousPersonalLeave(String applicantNameSnapshot, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        LeaveRequest adjacent = leaveRequestMapper.findFirstOverlappingOrAdjacent(
                applicantNameSnapshot,
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

    private List<ApprovalRuleStep> prepareCreationSteps(Long ruleId,
                                                        UserAccount operator,
                                                        String applicantType,
                                                        LeaveType leaveType) {
        List<ApprovalRuleStep> ruleSteps = requireRuleSteps(ruleId);
        List<ApprovalRuleStep> effectiveRuleSteps = shouldSkipHrApproval(operator, applicantType, leaveType)
                ? ruleSteps.stream().filter(step -> !isHrApprovalStep(step)).collect(Collectors.toList())
                : ruleSteps;
        ensureInitialOrgPrincipalStepKept(ruleSteps, effectiveRuleSteps);
        if (effectiveRuleSteps.isEmpty()) {
            throw new BizException("审批规则未配置有效步骤");
        }
        log.info("创建请假审批流: operatorId={}, operatorOrgUnitId={}, ruleId={}, originalSteps={}, finalSteps={}",
                operator == null ? null : operator.getId(),
                operator == null ? null : operator.getOrgUnitId(),
                ruleId,
                describeStepNos(ruleSteps),
                describeStepNos(effectiveRuleSteps));
        return effectiveRuleSteps;
    }

    private void swapPartySecretaryBeforeStationmaster(List<ApprovalRuleStep> steps) {
        int stationmasterIdx = -1;
        int partySecretaryIdx = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (RoleCode.STATIONMASTER.equals(steps.get(i).getApproverRoleCode())) {
                stationmasterIdx = i;
            } else if (RoleCode.PARTY_SECRETARY.equals(steps.get(i).getApproverRoleCode())) {
                partySecretaryIdx = i;
            }
        }
        if (stationmasterIdx >= 0 && partySecretaryIdx >= 0 && partySecretaryIdx > stationmasterIdx) {
            ApprovalRuleStep temp = steps.get(stationmasterIdx);
            steps.set(stationmasterIdx, steps.get(partySecretaryIdx));
            steps.set(partySecretaryIdx, temp);
        }
    }

    private boolean shouldSkipHrApproval(UserAccount operator, String applicantType, LeaveType leaveType) {
        if ((APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType) || APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(applicantType) || APPLICANT_TYPE_WORKSHOP_DIRECTOR.equals(applicantType))
                && isHrOrgUnit(operator.getOrgUnitId())) {
            return true;
        }
        if (leaveType == null || leaveType.getLeaveCode() == null) {
            return false;
        }
        if (!HR_APPROVAL_EXEMPT_LEAVE_CODES.contains(leaveType.getLeaveCode())) {
            return false;
        }
        return APPLICANT_TYPE_EMPLOYEE.equals(applicantType)
                || APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType)
                || APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(applicantType)
                || APPLICANT_TYPE_WORKSHOP_DIRECTOR.equals(applicantType);
    }

    private boolean isHrApprovalStep(ApprovalRuleStep step) {
        return ACTION_APPROVE.equals(step.getActionType())
                && APPROVER_SOURCE_HR_ORG.equals(step.getApproverSource())
                && RoleCode.HR_SECTION_CHIEF.equals(step.getApproverRoleCode());
    }

    private void ensureInitialOrgPrincipalStepKept(List<ApprovalRuleStep> ruleSteps, List<ApprovalRuleStep> finalSteps) {
        boolean ruleRequiresInitialOrgApproval = ruleSteps.stream()
                .anyMatch(this::isInitialOrgPrincipalApproval);
        if (!ruleRequiresInitialOrgApproval) {
            return;
        }
        boolean finalKeepsInitialOrgApproval = finalSteps.stream()
                .anyMatch(this::isInitialOrgPrincipalApproval);
        if (!finalKeepsInitialOrgApproval) {
            throw new BizException("非劳动人事科发起请假必须经过科室车间负责人审批");
        }
    }

    private String describeStepNos(List<ApprovalRuleStep> steps) {
        return steps.stream()
                .map(step -> step.getStepNo() + ":" + step.getApproverRoleCode() + ":" + step.getActionType())
                .collect(Collectors.joining(","));
    }

    private boolean isInitialOrgPrincipalApproval(ApprovalRuleStep step) {
        return Integer.valueOf(1).equals(step.getStepNo())
                && ACTION_APPROVE.equals(step.getActionType())
                && RoleCode.ORG_PRINCIPAL.equals(step.getApproverRoleCode());
    }

    private boolean isHrAttendanceAdmin(UserAccount operator) {
        if (operator == null || !RoleCode.ATTENDANCE_ADMIN.equals(operator.getRoleCode())) {
            return false;
        }
        return isHrOrgUnit(operator.getOrgUnitId());
    }

    private boolean isHrOrgUnit(Long orgUnitId) {
        if (orgUnitId == null) {
            return false;
        }
        OrgUnit orgUnit = orgUnitMapper.findById(orgUnitId);
        if (orgUnit == null) {
            return false;
        }
        String orgCode = orgUnit.getOrgCode() == null ? "" : orgUnit.getOrgCode().trim();
        String orgName = orgUnit.getOrgName() == null ? "" : orgUnit.getOrgName().trim();
        return "D04".equalsIgnoreCase(orgCode) || orgName.contains("劳动人事科");
    }

    private ApprovalRuleStep toHrApprovalStep(ApprovalRuleStep source) {
        ApprovalRuleStep step = new ApprovalRuleStep();
        step.setId(source.getId());
        step.setRuleId(source.getRuleId());
        step.setStepNo(source.getStepNo());
        step.setActionType(ACTION_APPROVE);
        step.setAssigneeCount(0);
        step.setCandidateGroup(source.getCandidateGroup());
        step.setStepCode("HR_APPROVE");
        step.setStepCodeName("劳动人事科科长审批");
        step.setStepName("劳动人事科科长审批（电子签名）");
        step.setApproverSource(APPROVER_SOURCE_HR_ORG);
        step.setApproverRoleCode(RoleCode.HR_SECTION_CHIEF);
        step.setApproverRoleName("劳动人事科科长");
        step.setReturnToOrg(source.getReturnToOrg());
        return step;
    }

    private ApprovalRuleStep requireRuleStep(Long ruleId, Long ruleStepId) {
        return requireRuleSteps(ruleId).stream()
                .filter(step -> step.getId().equals(ruleStepId))
                .findFirst()
                .orElseThrow(() -> new BizException("审批步骤不存在"));
    }

    private UserAccount resolveInitialApprover(Long applicantOrgUnitId, ApprovalRuleStep step) {
        if (APPROVER_SOURCE_APPLICANT_ORG.equals(step.getApproverSource())
                && RoleCode.ORG_PRINCIPAL.equals(step.getApproverRoleCode())
                && isHrOrgUnit(applicantOrgUnitId)) {
            return userAccountMapper.findByRole(RoleCode.HR_SECTION_CHIEF);
        }
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
        SelectionScenario scenario = determineSelectionScenario(request);
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

        List<UserAccount> selectedUsers = approverUserIds.stream()
                .map(this::requireUser)
                .collect(Collectors.toList());
        validateSelectedApproverRoles(scenario, selectedUsers);
        return selectedUsers;
    }

    private List<SelectedApproverResponse> resolveSelectableApprovers(LeaveRequest request,
                                                                      LeaveApproval pending,
                                                                      ApprovalRuleStep currentRuleStep) {
        List<UserAccount> candidates = switch (determineSelectionScenario(request)) {
            case SICK_WITHIN_MONTH -> findEnabledUsersByRoles(List.of(RoleCode.DEPUTY_STATIONMASTER));
            case SICK_OVER_MONTH -> resolveSickOverMonthCandidates(currentRuleStep);
            case SECTION_LEVEL -> findEnabledUsersByRoles(List.of(
                    RoleCode.DEPUTY_STATIONMASTER,
                    RoleCode.STATIONMASTER,
                    RoleCode.PARTY_SECRETARY));
            case PERSONAL_5_TO_10 -> findEnabledUsersByRoles(List.of(RoleCode.DEPUTY_STATIONMASTER));
            case PERSONAL_10_TO_30 -> findEnabledUsersByRoles(resolveStationmasterCandidateRoles(request));
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

    private void validateSelectedApproverRoles(SelectionScenario scenario, List<UserAccount> selectedUsers) {
        if (SelectionScenario.SECTION_LEVEL.equals(scenario)) {
            long deputyCount = selectedUsers.stream()
                    .filter(user -> RoleCode.DEPUTY_STATIONMASTER.equals(user.getRoleCode()))
                    .count();
            long stationmasterCount = selectedUsers.stream()
                    .filter(user -> RoleCode.STATIONMASTER.equals(user.getRoleCode()))
                    .count();
            long partySecretaryCount = selectedUsers.stream()
                    .filter(user -> RoleCode.PARTY_SECRETARY.equals(user.getRoleCode()))
                    .count();
            if (deputyCount != 1 || stationmasterCount != 1 || partySecretaryCount != 1) {
                throw new BizException("中层正职流程必须各选择1名副站长、站长、党委书记");
            }
            return;
        }
        if (SelectionScenario.SICK_OVER_MONTH.equals(scenario)) {
            long deputyCount = selectedUsers.stream()
                    .filter(user -> RoleCode.DEPUTY_STATIONMASTER.equals(user.getRoleCode()))
                    .count();
            long stationmasterCount = selectedUsers.stream()
                    .filter(user -> RoleCode.STATIONMASTER.equals(user.getRoleCode()))
                    .count();
            if (deputyCount != 1 || stationmasterCount != 1) {
                throw new BizException("病假超30天流程必须各选择1名副站长和站长");
            }
        }
    }

    private List<UserAccount> resolveSickOverMonthCandidates(ApprovalRuleStep currentRuleStep) {
        List<UserAccount> candidates = new ArrayList<>();
        candidates.addAll(findEnabledUsersByRoles(List.of(RoleCode.DEPUTY_STATIONMASTER)));
        candidates.addAll(findEnabledUsersByRoles(List.of(RoleCode.STATIONMASTER)));
        return candidates;
    }

    private List<String> resolveStationmasterCandidateRoles(LeaveRequest request) {
        if (APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(request.getApplicantType())
                || POSITION_SECTION_LEVEL.equals(request.getPositionLevelCode())) {
            return List.of(RoleCode.STATIONMASTER, RoleCode.PARTY_SECRETARY);
        }
        return List.of(RoleCode.STATIONMASTER);
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
            case RoleCode.PARTY_SECRETARY -> CANDIDATE_GROUP_PARTY_AND_PRINCIPAL;
            case RoleCode.WORKSHOP_PARTY_SECRETARY -> CANDIDATE_GROUP_PARTY_AND_PRINCIPAL;
            default -> null;
        };
    }

    private SelectionScenario determineSelectionScenario(LeaveRequest request) {
        if (APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(request.getApplicantType())
                || POSITION_SECTION_LEVEL.equals(request.getPositionLevelCode())) {
            return SelectionScenario.SECTION_LEVEL;
        }
        LeaveType leaveType = requireLeaveType(request.getLeaveTypeId());
        String leaveScope = resolveLeaveScope(leaveType);
        BigDecimal leaveDays = request.getLeaveDays();
        if (leaveDays == null) {
            return SelectionScenario.NONE;
        }
        int daysInMonth = request.getStartDate() != null ? request.getStartDate().lengthOfMonth() : 30;
        BigDecimal monthThreshold = BigDecimal.valueOf(daysInMonth);

        if (LEAVE_SCOPE_SICK.equals(leaveScope)) {
            if (leaveDays.compareTo(monthThreshold) > 0) {
                return SelectionScenario.SICK_OVER_MONTH;
            }
            if (leaveDays.compareTo(DAY_7) > 0) {
                return SelectionScenario.SICK_WITHIN_MONTH;
            }
        }

        if (LEAVE_SCOPE_PERSONAL.equals(leaveScope)) {
            if (leaveDays.compareTo(monthThreshold) > 0) {
                return SelectionScenario.PERSONAL_OVER_30;
            }
            if (leaveDays.compareTo(DAY_5) > 0 && leaveDays.compareTo(DAY_10) <= 0) {
                return SelectionScenario.PERSONAL_5_TO_10;
            }
            if (leaveDays.compareTo(DAY_10) > 0) {
                return SelectionScenario.PERSONAL_10_TO_30;
            }
        }

        if (LEAVE_SCOPE_OTHER.equals(leaveScope)) {
            return SelectionScenario.SECTION_LEVEL;
        }
        return SelectionScenario.NONE;
    }

    private void ensureCurrentActor(UserAccount operator, LeaveRequest request, LeaveApproval pending) {
        if (pending.getApproverUserId() != null) {
            if (!pending.getApproverUserId().equals(operator.getId())) {
                throw new BizException("当前账号无权处理该节点");
            }
            if (isSelectedApproverNode(request, pending)) {
                if (!pending.getApproverRoleCode().equals(operator.getRoleCode())) {
                    throw new BizException("当前账号无权处理该节点");
                }
                return;
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

    private boolean isSelectedApproverNode(LeaveRequest request, LeaveApproval pending) {
        ApprovalRuleStep step = requireRuleStep(request.getApprovalRuleId(), pending.getRuleStepId());
        return APPROVER_SOURCE_SELECTED.equals(step.getApproverSource());
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
        if (canApproveSectionLevelSickOverMonth(permission, request, leaveScope)) {
            return true;
        }
        if (permission.getApplicantType() != null
                && !permission.getApplicantType().equals(resolveRuleApplicantType(request.getApplicantType(), leaveScope))) {
            return false;
        }
        if (permission.getPositionLevelCode() != null
                && !permission.getPositionLevelCode().equals(
                resolveRulePositionLevel(request.getApplicantType(), request.getPositionLevelCode(), leaveScope))) {
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

    private boolean canApproveSectionLevelSickOverMonth(ApprovalPermission permission, LeaveRequest request, String leaveScope) {
        int daysInMonth = request.getStartDate() != null ? request.getStartDate().lengthOfMonth() : 30;
        return (RoleCode.STATIONMASTER.equals(permission.getRoleCode())
                || RoleCode.PARTY_SECRETARY.equals(permission.getRoleCode()))
                && APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(request.getApplicantType())
                && POSITION_SECTION_LEVEL.equals(request.getPositionLevelCode())
                && LEAVE_SCOPE_SICK.equals(leaveScope)
                && request.getLeaveDays() != null
                && request.getLeaveDays().compareTo(BigDecimal.valueOf(daysInMonth)) > 0;
    }

    private String resolveRuleApplicantType(String applicantType, String leaveScope) {
        if (APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType)
                && (LEAVE_SCOPE_SICK.equals(leaveScope) || LEAVE_SCOPE_PERSONAL.equals(leaveScope))) {
            return APPLICANT_TYPE_EMPLOYEE;
        }
        if (APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType) || APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(applicantType)) {
            return APPLICANT_TYPE_CADRE;
        }
        return applicantType;
    }

    private String resolveRulePositionLevel(String applicantType, String actualPositionLevel, String leaveScope) {
        if (APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType)
                && (LEAVE_SCOPE_SICK.equals(leaveScope) || LEAVE_SCOPE_PERSONAL.equals(leaveScope))) {
            return POSITION_STAFF;
        }
        return resolvePositionLevel(applicantType, actualPositionLevel);
    }

    private void decideApproval(LeaveApproval approval, Long approverUserId, Boolean approved, String comment, String signatureUrl, java.time.LocalDate signatureDate, LocalDateTime approvedAt) {
        approval.setApproverUserId(approverUserId);
        approval.setApprovalStatus(Boolean.TRUE.equals(approved) ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        approval.setApprovalComment(comment);
        approval.setSignatureUrl(normalizeSignatureUrl(signatureUrl));
        approval.setSignatureDate(signatureDate != null ? signatureDate : java.time.LocalDate.now());
        approval.setApprovedAt(approvedAt != null ? approvedAt : LocalDateTime.now());
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

    private void ensureNoTemporarySignatureOverride(org.springframework.web.multipart.MultipartFile signatureFile,
                                                    String submittedSignatureUrl,
                                                    BatchSignaturePayload batchSignaturePayload,
                                                    String configuredSignatureUrl) {
        if ((signatureFile != null && !signatureFile.isEmpty()) || batchSignaturePayload != null) {
            throw new BizException("电子签名只能由超级管理员预先上传，审批时不允许临时上传或覆盖");
        }

        String normalizedSubmittedUrl = normalizeSignatureUrl(submittedSignatureUrl);
        if (normalizedSubmittedUrl == null) {
            return;
        }
        if (sameSignatureUrl(normalizedSubmittedUrl, configuredSignatureUrl)) {
            return;
        }
        throw new BizException("电子签名只能由超级管理员预先上传，审批时不允许临时上传或覆盖");
    }

    private boolean sameSignatureUrl(String first, String second) {
        String normalizedFirst = canonicalizeSignatureUrl(first);
        String normalizedSecond = canonicalizeSignatureUrl(second);
        return normalizedFirst != null && normalizedFirst.equals(normalizedSecond);
    }

    private String canonicalizeSignatureUrl(String signatureUrl) {
        String normalized = normalizeSignatureUrl(signatureUrl);
        if (normalized == null) {
            return null;
        }
        int filePrefixIndex = normalized.indexOf(FILE_URL_PREFIX);
        if (filePrefixIndex >= 0) {
            return normalized.substring(filePrefixIndex);
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
                .signatureDate(approval.getSignatureDate())
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
        SECTION_LEVEL,
        SICK_WITHIN_MONTH,
        SICK_OVER_MONTH,
        PERSONAL_5_TO_10,
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
        return requireUser(dto.getApplicantId());
    }

    private String resolveApplicantNameSnapshot(String applicantType, UserAccount applicant, CreateLeaveRequestDto dto) {
        String inputName = dto.getApplicantName() == null ? null : dto.getApplicantName().trim();
        if ((APPLICANT_TYPE_EMPLOYEE.equals(applicantType)
                || APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType)
                || APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(applicantType)
                || APPLICANT_TYPE_WORKSHOP_DIRECTOR.equals(applicantType))
                && inputName != null && !inputName.isBlank()) {
            return inputName;
        }
        return applicant.getEmpName();
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

    private LeaveApproval requireCurrentPendingApproval(LeaveRequest request) {
        LeaveApproval approval = leaveApprovalMapper.findPendingByStep(request.getId(), request.getCurrentStep());
        if (approval == null) {
            throw new BizException("当前请假单不存在待处理节点");
        }
        return approval;
    }

    private String toRoleName(String roleCode) {
        return switch (roleCode) {
            case RoleCode.ATTENDANCE_ADMIN -> "考勤管理员";
            case RoleCode.ORG_PRINCIPAL -> "科室车间负责人";
            case RoleCode.WORKSHOP_PARTY_SECRETARY -> "车间书记";
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
