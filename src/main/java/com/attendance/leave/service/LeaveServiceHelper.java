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
import com.attendance.leave.dto.LeaveListItemResponse;
import com.attendance.leave.dto.SelectedApproverResponse;
import com.attendance.leave.enums.ApprovalStatus;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveServiceHelper {

    public static final String FILE_URL_PREFIX = "/files/";
    public static final String LEGACY_FILE_HOST = "http://192.168.1.10:8080";

    public static final String APPLICANT_TYPE_EMPLOYEE = "EMPLOYEE";
    public static final String APPLICANT_TYPE_CADRE = "CADRE";
    public static final String APPLICANT_TYPE_GENERAL_CADRE = "GENERAL_CADRE";
    public static final String APPLICANT_TYPE_SECTION_LEVEL_CADRE = "SECTION_LEVEL_CADRE";
    public static final String APPLICANT_TYPE_WORKSHOP_DIRECTOR = "WORKSHOP_DIRECTOR";
    public static final String POSITION_STAFF = "STAFF";
    public static final String POSITION_GENERAL_CADRE = "GENERAL_CADRE";
    public static final String POSITION_SECTION_LEVEL = "SECTION_LEVEL";
    public static final String POSITION_WORKSHOP_DIRECTOR = "WORKSHOP_DIRECTOR";
    public static final String LEAVE_SCOPE_ALL = "ALL";
    public static final String LEAVE_SCOPE_OTHER = "OTHER";
    public static final String LEAVE_SCOPE_SICK = "SICK";
    public static final String LEAVE_SCOPE_PERSONAL = "PERSONAL";
    public static final Set<String> HR_APPROVAL_EXEMPT_LEAVE_CODES = Set.of("年", "丧", "搬", "病", "事");
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_SELECT = "SELECT";
    public static final String APPROVER_SOURCE_APPLICANT_ORG = "APPLICANT_ORG";
    public static final String APPROVER_SOURCE_HR_ORG = "HR_ORG";
    public static final String APPROVER_SOURCE_SELECTED = "SELECTED";
    public static final String CANDIDATE_GROUP_SUPERVISOR = "SUPERVISOR_LEADER";
    public static final String CANDIDATE_GROUP_STATIONMASTER = "STATIONMASTER";
    public static final String CANDIDATE_GROUP_PARTY_AND_PRINCIPAL = "PARTY_AND_PRINCIPAL";
    public static final BigDecimal DAY_1 = BigDecimal.ONE;
    public static final BigDecimal DAY_2 = BigDecimal.valueOf(2);
    public static final BigDecimal DAY_5 = BigDecimal.valueOf(5);
    public static final BigDecimal DAY_7 = BigDecimal.valueOf(7);
    public static final BigDecimal DAY_10 = BigDecimal.valueOf(10);
    public static final BigDecimal DAY_30 = BigDecimal.valueOf(30);
    public static final BigDecimal DAY_60 = BigDecimal.valueOf(60);
    public static final List<String> EFFECTIVE_LEAVE_STATUSES = List.of(
            LeaveRequestStatus.PENDING,
            LeaveRequestStatus.APPROVING,
            LeaveRequestStatus.APPROVED
    );

    public final UserAccountMapper userAccountMapper;
    public final LeaveTypeMapper leaveTypeMapper;
    public final LeaveRequestMapper leaveRequestMapper;
    public final LeaveApprovalMapper leaveApprovalMapper;
    public final ApprovalRuleMapper approvalRuleMapper;
    public final ApprovalRuleStepMapper approvalRuleStepMapper;
    public final LeaveSignRequirementService leaveSignRequirementService;
    public final ApprovalPermissionMapper approvalPermissionMapper;
    public final OrgUnitMapper orgUnitMapper;
    public final LeaveDocumentService leaveDocumentService;

    @Value("${attendance.file-storage-path:uploads}")
    public String fileStoragePath;

    // ==================== 用户/实体查找 ====================

    public UserAccount requireCurrentUser() {
        CurrentUser currentUser = UserContext.get();
        if (currentUser == null || currentUser.getUserId() == null) {
            throw new BizException("未登录或 token 已失效");
        }
        return requireUser(currentUser.getUserId());
    }

    public UserAccount requireUser(Long userId) {
        UserAccount user = userAccountMapper.findById(userId);
        if (user == null) {
            throw new BizException("用户不存在: " + userId);
        }
        return user;
    }

    public LeaveType requireLeaveType(Long leaveTypeId) {
        LeaveType leaveType = leaveTypeMapper.findById(leaveTypeId);
        if (leaveType == null) {
            throw new BizException("假别不存在");
        }
        return leaveType;
    }

    public LeaveRequest requireLeaveRequest(Long leaveId) {
        LeaveRequest request = leaveRequestMapper.findById(leaveId);
        if (request == null) {
            throw new BizException("请假单不存在");
        }
        return request;
    }

    public LeaveApproval requireCurrentPendingApproval(LeaveRequest request) {
        LeaveApproval approval = leaveApprovalMapper.findPendingByStep(request.getId(), request.getCurrentStep());
        if (approval == null) {
            throw new BizException("当前请假单不存在待处理节点");
        }
        return approval;
    }

    // ==================== 权限/校验 ====================

    public void ensureNotCancelled(LeaveRequest request) {
        if (LeaveRequestStatus.CANCELLED.equals(request.getStatus())) {
            throw new BizException("请假单已取消，不能继续处理");
        }
    }

    public void ensureEditableRejectedByAdmin(UserAccount operator, LeaveRequest request) {
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

    public void ensureCurrentActor(UserAccount operator, LeaveRequest request, LeaveApproval pending) {
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

    public boolean isSelectedApproverNode(LeaveRequest request, LeaveApproval pending) {
        ApprovalRuleStep step = requireRuleStep(request.getApprovalRuleId(), pending.getRuleStepId());
        return APPROVER_SOURCE_SELECTED.equals(step.getApproverSource());
    }

    void requireApprovalPermission(UserAccount operator, LeaveRequest request, LeaveApproval pending) {
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

    public boolean matchesApprovalPermission(ApprovalPermission permission, LeaveRequest request, String leaveScope) {
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

    boolean canApproveSectionLevelSickOverMonth(ApprovalPermission permission, LeaveRequest request, String leaveScope) {
        int daysInMonth = request.getStartDate() != null ? request.getStartDate().lengthOfMonth() : 30;
        return (RoleCode.STATIONMASTER.equals(permission.getRoleCode())
                || RoleCode.PARTY_SECRETARY.equals(permission.getRoleCode()))
                && APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(request.getApplicantType())
                && POSITION_SECTION_LEVEL.equals(request.getPositionLevelCode())
                && LEAVE_SCOPE_SICK.equals(leaveScope)
                && request.getLeaveDays() != null
                && request.getLeaveDays().compareTo(BigDecimal.valueOf(daysInMonth)) > 0;
    }

    // ==================== 审批规则 ====================

    public ApprovalRule resolveApprovalRule(String applicantType, String actualPositionLevel, LeaveType leaveType, BigDecimal leaveDays, boolean exceedsOneMonth) {
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

    public boolean matchesPersonalLeaveRuleName(ApprovalRule rule, BigDecimal leaveDays, String leaveScope) {
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

    public String resolvePositionLevel(String applicantType, String actualPositionLevel) {
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

    public String normalizeApplicantType(String applicantType) {
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

    public boolean matchesScope(String ruleScope, String actualScope) {
        return LEAVE_SCOPE_ALL.equals(ruleScope) || ruleScope.equals(actualScope);
    }

    public boolean matchesDays(ApprovalRule rule, BigDecimal leaveDays) {
        if (leaveDays == null) {
            return true;
        }
        if (rule.getMinDays() != null && leaveDays.compareTo(rule.getMinDays()) < 0) {
            return false;
        }
        return rule.getMaxDays() == null || leaveDays.compareTo(rule.getMaxDays()) <= 0;
    }

    public boolean matchesExceedsMonth(ApprovalRule rule, boolean exceedsOneMonth) {
        if (LEAVE_SCOPE_ALL.equals(rule.getLeaveScope())) {
            return true;
        }
        if (rule.getExceedsMonthOnly() == null) {
            return true;
        }
        return exceedsOneMonth == (rule.getExceedsMonthOnly() == 1);
    }

    public String resolveLeaveScope(LeaveType leaveType) {
        if ("病".equals(leaveType.getLeaveCode())) {
            return LEAVE_SCOPE_SICK;
        }
        if ("事".equals(leaveType.getLeaveCode())) {
            return LEAVE_SCOPE_PERSONAL;
        }
        return LEAVE_SCOPE_OTHER;
    }

    public String resolveRuleApplicantType(String applicantType, String leaveScope) {
        if (APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType)
                && (LEAVE_SCOPE_SICK.equals(leaveScope) || LEAVE_SCOPE_PERSONAL.equals(leaveScope))) {
            return APPLICANT_TYPE_EMPLOYEE;
        }
        if (APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType) || APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(applicantType)) {
            return APPLICANT_TYPE_CADRE;
        }
        return applicantType;
    }

    public String resolveRulePositionLevel(String applicantType, String actualPositionLevel, String leaveScope) {
        if (APPLICANT_TYPE_GENERAL_CADRE.equals(applicantType)
                && (LEAVE_SCOPE_SICK.equals(leaveScope) || LEAVE_SCOPE_PERSONAL.equals(leaveScope))) {
            return POSITION_STAFF;
        }
        return resolvePositionLevel(applicantType, actualPositionLevel);
    }

    // ==================== 审批步骤 ====================

    public List<ApprovalRuleStep> requireRuleSteps(Long ruleId) {
        List<ApprovalRuleStep> steps = approvalRuleStepMapper.findByRuleId(ruleId);
        if (steps.isEmpty()) {
            throw new BizException("审批规则未配置步骤");
        }
        return steps;
    }

    public ApprovalRuleStep requireRuleStep(Long ruleId, Long ruleStepId) {
        return requireRuleSteps(ruleId).stream()
                .filter(step -> step.getId().equals(ruleStepId))
                .findFirst()
                .orElseThrow(() -> new BizException("审批步骤不存在"));
    }

    public List<ApprovalRuleStep> prepareCreationSteps(Long ruleId, UserAccount operator, String applicantType, LeaveType leaveType) {
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

    public void swapPartySecretaryBeforeStationmaster(List<ApprovalRuleStep> steps) {
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

    public boolean shouldSkipHrApproval(UserAccount operator, String applicantType, LeaveType leaveType) {
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

    public boolean isHrApprovalStep(ApprovalRuleStep step) {
        return ACTION_APPROVE.equals(step.getActionType())
                && APPROVER_SOURCE_HR_ORG.equals(step.getApproverSource())
                && RoleCode.HR_SECTION_CHIEF.equals(step.getApproverRoleCode());
    }

    public void ensureInitialOrgPrincipalStepKept(List<ApprovalRuleStep> ruleSteps, List<ApprovalRuleStep> finalSteps) {
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

    public String describeStepNos(List<ApprovalRuleStep> steps) {
        return steps.stream()
                .map(step -> step.getStepNo() + ":" + step.getApproverRoleCode() + ":" + step.getActionType())
                .collect(Collectors.joining(","));
    }

    public boolean isInitialOrgPrincipalApproval(ApprovalRuleStep step) {
        return Integer.valueOf(1).equals(step.getStepNo())
                && ACTION_APPROVE.equals(step.getActionType())
                && RoleCode.ORG_PRINCIPAL.equals(step.getApproverRoleCode());
    }

    public boolean isHrAttendanceAdmin(UserAccount operator) {
        if (operator == null || !RoleCode.ATTENDANCE_ADMIN.equals(operator.getRoleCode())) {
            return false;
        }
        return isHrOrgUnit(operator.getOrgUnitId());
    }

    public boolean isHrOrgUnit(Long orgUnitId) {
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

    // ==================== 审批人解析 ====================

    public UserAccount resolveInitialApprover(Long applicantOrgUnitId, ApprovalRuleStep step) {
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

    public List<UserAccount> validateAndResolveSelectedApprovers(LeaveRequest request, LeaveApproval pending,
                                                          ApprovalRuleStep currentRuleStep, List<Long> approverUserIds) {
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

    public List<SelectedApproverResponse> resolveSelectableApprovers(LeaveRequest request, LeaveApproval pending,
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

    public void validateSelectedApproverRoles(SelectionScenario scenario, List<UserAccount> selectedUsers) {
        if (SelectionScenario.SECTION_LEVEL.equals(scenario)) {
            long deputyCount = selectedUsers.stream()
                    .filter(user -> RoleCode.DEPUTY_STATIONMASTER.equals(user.getRoleCode())).count();
            long stationmasterCount = selectedUsers.stream()
                    .filter(user -> RoleCode.STATIONMASTER.equals(user.getRoleCode())).count();
            long partySecretaryCount = selectedUsers.stream()
                    .filter(user -> RoleCode.PARTY_SECRETARY.equals(user.getRoleCode())).count();
            if (deputyCount != 1 || stationmasterCount != 1 || partySecretaryCount != 1) {
                throw new BizException("中层正职流程必须各选择1名副站长、站长、党委书记");
            }
            return;
        }
        if (SelectionScenario.SICK_OVER_MONTH.equals(scenario)) {
            long deputyCount = selectedUsers.stream()
                    .filter(user -> RoleCode.DEPUTY_STATIONMASTER.equals(user.getRoleCode())).count();
            long stationmasterCount = selectedUsers.stream()
                    .filter(user -> RoleCode.STATIONMASTER.equals(user.getRoleCode())).count();
            if (deputyCount != 1 || stationmasterCount != 1) {
                throw new BizException("病假超30天流程必须各选择1名副站长和站长");
            }
        }
    }

    public List<UserAccount> resolveSickOverMonthCandidates(ApprovalRuleStep currentRuleStep) {
        List<UserAccount> candidates = new ArrayList<>();
        candidates.addAll(findEnabledUsersByRoles(List.of(RoleCode.DEPUTY_STATIONMASTER)));
        candidates.addAll(findEnabledUsersByRoles(List.of(RoleCode.STATIONMASTER)));
        return candidates;
    }

    public List<String> resolveStationmasterCandidateRoles(LeaveRequest request) {
        if (APPLICANT_TYPE_SECTION_LEVEL_CADRE.equals(request.getApplicantType())
                || POSITION_SECTION_LEVEL.equals(request.getPositionLevelCode())) {
            return List.of(RoleCode.STATIONMASTER, RoleCode.PARTY_SECRETARY);
        }
        return List.of(RoleCode.STATIONMASTER);
    }

    public List<UserAccount> findEnabledUsersByRoles(List<String> roleCodes) {
        return userAccountMapper.findAll().stream()
                .filter(user -> user.getIsEnabled() != null && user.getIsEnabled() == 1)
                .filter(user -> roleCodes.contains(user.getRoleCode()))
                .sorted(Comparator.comparing(UserAccount::getId))
                .collect(Collectors.toList());
    }

    public String resolveCandidateGroupByRole(String roleCode) {
        return switch (roleCode) {
            case RoleCode.DEPUTY_STATIONMASTER -> CANDIDATE_GROUP_SUPERVISOR;
            case RoleCode.STATIONMASTER -> CANDIDATE_GROUP_STATIONMASTER;
            case RoleCode.PARTY_SECRETARY -> CANDIDATE_GROUP_PARTY_AND_PRINCIPAL;
            case RoleCode.WORKSHOP_PARTY_SECRETARY -> CANDIDATE_GROUP_PARTY_AND_PRINCIPAL;
            default -> null;
        };
    }

    public List<LeaveApproval> resolveOrCreateSelectedApprovalTargets(LeaveRequest request,
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
        selectedSteps = selectedSteps.stream().limit(targetCount).collect(Collectors.toList());

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

    public List<ApprovalRuleStep> sortSectionLevelSelectedSteps(List<ApprovalRuleStep> selectedSteps,
                                                          List<UserAccount> selectedUsers,
                                                          boolean partySecretaryFirst) {
        Map<String, ApprovalRuleStep> stepByRoleCode = selectedSteps.stream()
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

    public SelectionScenario determineSelectionScenario(LeaveRequest request) {
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

    // ==================== 审批操作 ====================

    public void decideApproval(LeaveApproval approval, Long approverUserId, Boolean approved, String comment,
                        String signatureUrl, LocalDate signatureDate, LocalDateTime approvedAt) {
        approval.setApproverUserId(approverUserId);
        approval.setApprovalStatus(Boolean.TRUE.equals(approved) ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        approval.setApprovalComment(comment);
        approval.setSignatureUrl(normalizeSignatureUrl(signatureUrl));
        approval.setSignatureDate(signatureDate != null ? signatureDate : LocalDate.now());
        approval.setApprovedAt(approvedAt != null ? approvedAt : LocalDateTime.now());
        leaveApprovalMapper.updateDecision(approval);
    }

    public void moveToNextStep(LeaveRequest request) {
        LeaveApproval nextPending = leaveApprovalMapper.findFirstPending(request.getId());
        if (nextPending == null) {
            request.setStatus(LeaveRequestStatus.APPROVED);
            request.setCurrentStep(com.attendance.leave.enums.ApprovalStep.FINISHED);
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

    public LeaveApproval buildApproval(Long leaveRequestId, ApprovalRuleStep step, Long approverUserId) {
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

    public void swapApprovalStepNoIfNeeded(LeaveRequest request, List<LeaveApproval> approvals) {
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

    // ==================== 查询/转换 ====================

    public ApprovalRecordResponse toApprovalRecordResponse(LeaveApproval approval) {
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

    public List<LeaveListItemResponse> toLeaveListItemResponses(List<LeaveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<Long> leaveRequestIds = requests.stream().map(LeaveRequest::getId).toList();
        List<LeaveApproval> allApprovals = leaveApprovalMapper.findByLeaveRequestIds(leaveRequestIds);
        Map<Long, List<String>> approvedRolesByLeaveId = buildApprovedRolesByLeaveId(allApprovals);
        Map<Long, List<ApprovalRecordResponse>> approvalsByLeaveId = buildApprovalsByLeaveId(allApprovals);
        return requests.stream()
                .map(request -> toLeaveListItemResponse(
                        request,
                        approvedRolesByLeaveId.getOrDefault(request.getId(), List.of()),
                        approvalsByLeaveId.getOrDefault(request.getId(), List.of())))
                .collect(Collectors.toList());
    }

    public LeaveListItemResponse toLeaveListItemResponse(LeaveRequest request, List<String> approvedRoles,
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

    public Map<Long, List<ApprovalRecordResponse>> buildApprovalsByLeaveId(List<LeaveApproval> approvals) {
        if (approvals == null || approvals.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<ApprovalRecordResponse>> result = new HashMap<>();
        for (LeaveApproval approval : approvals) {
            result.computeIfAbsent(approval.getLeaveRequestId(), key -> new ArrayList<>())
                    .add(toApprovalRecordResponse(approval));
        }
        return result;
    }

    public Map<Long, List<String>> buildApprovedRolesByLeaveId(List<LeaveApproval> approvals) {
        if (approvals == null || approvals.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, LinkedHashMap<String, Boolean>> roleSetByLeaveId = new HashMap<>();
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

        Map<Long, List<String>> approvedRolesByLeaveId = new HashMap<>();
        roleSetByLeaveId.forEach((leaveId, roleSet) ->
                approvedRolesByLeaveId.put(leaveId, new ArrayList<>(roleSet.keySet())));
        return approvedRolesByLeaveId;
    }

    // ==================== 文件处理 ====================

    public String saveHandwrittenSignatureFile(Long leaveId, String type, String originalName, java.io.InputStream inputStream) throws IOException {
        Path directory = Paths.get(fileStoragePath, "handwritten-signatures");
        Files.createDirectories(directory);
        String extension = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.')) : ".png";
        String filename = "leave_" + leaveId + "_" + type.toLowerCase() + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = directory.resolve(filename);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return "/files/handwritten-signatures/" + filename;
    }

    public String saveSignatureFile(Long leaveId, Integer stepNo, String originalName, java.io.InputStream inputStream) throws IOException {
        Path directory = Paths.get(fileStoragePath, "approval-signatures");
        Files.createDirectories(directory);
        String extension = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.')) : ".png";
        String filename = "leave_" + leaveId + "_step_" + stepNo + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = directory.resolve(filename);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return "/files/approval-signatures/" + filename;
    }

    public String normalizeSignatureUrl(String signatureUrl) {
        if (signatureUrl == null || signatureUrl.isBlank() || "undefined".equalsIgnoreCase(signatureUrl.trim())) {
            return null;
        }
        String normalized = signatureUrl.trim();
        if (normalized.startsWith(LEGACY_FILE_HOST + FILE_URL_PREFIX)) {
            return normalized.replace(LEGACY_FILE_HOST, "");
        }
        return normalized;
    }

    public String canonicalizeSignatureUrl(String signatureUrl) {
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

    public boolean sameSignatureUrl(String first, String second) {
        String normalizedFirst = canonicalizeSignatureUrl(first);
        String normalizedSecond = canonicalizeSignatureUrl(second);
        return normalizedFirst != null && normalizedFirst.equals(normalizedSecond);
    }

    public void ensureNoTemporarySignatureOverride(org.springframework.web.multipart.MultipartFile signatureFile,
                                             String submittedSignatureUrl,
                                             byte[] batchSignatureBytes,
                                             String configuredSignatureUrl) {
        if ((signatureFile != null && !signatureFile.isEmpty()) || batchSignatureBytes != null) {
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

    // ==================== 请假规则校验 ====================

    public void validateLeaveRequestRules(String applicantNameSnapshot, LeaveType leaveType, com.attendance.leave.dto.CreateLeaveRequestDto dto) {
        if (dto.getStartTime().isAfter(dto.getEndTime())) {
            throw new BizException("结束时间不能早于开始时间");
        }
        if (!LEAVE_SCOPE_PERSONAL.equals(resolveLeaveScope(leaveType))) {
            return;
        }
        validatePersonalLeaveRules(applicantNameSnapshot, dto);
    }

    public void validatePersonalLeaveRules(String applicantNameSnapshot, com.attendance.leave.dto.CreateLeaveRequestDto dto) {
        BigDecimal leaveDays = dto.getLeaveDays();
        if (leaveDays == null) {
            return;
        }

        LocalDate startDate = dto.getStartTime().toLocalDate();
        int daysInMonth = startDate.lengthOfMonth();
        BigDecimal monthThreshold = BigDecimal.valueOf(daysInMonth);

        if (leaveDays.compareTo(monthThreshold) >= 0 && leaveDays.compareTo(DAY_60) > 0) {
            throw new BizException("特殊情况单次事假原则上不得超过2个月");
        }

        LocalDate endDate = dto.getEndTime().toLocalDate();
        LocalDate monthStart = startDate.withDayOfMonth(1);
        LocalDate monthEnd = startDate.withDayOfMonth(daysInMonth);
        LocalDate quarterStart = startDate.withMonth(firstMonthOfQuarter(startDate.getMonth()).getValue()).withDayOfMonth(1);
        LocalDate quarterEnd = quarterStart.plusMonths(2).withDayOfMonth(quarterStart.plusMonths(2).lengthOfMonth());
        LocalDate yearStart = startDate.withDayOfYear(1);
        LocalDate yearEnd = startDate.withDayOfYear(startDate.lengthOfYear());

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

    private long countPersonalLeave(String applicantNameSnapshot, LocalDate periodStart, LocalDate periodEnd,
                                     BigDecimal minDays, BigDecimal maxDays) {
        Long count = leaveRequestMapper.countLeaveRequestsByApplicantAndRange(
                applicantNameSnapshot, requirePersonalLeaveTypeId(), EFFECTIVE_LEAVE_STATUSES,
                periodStart, periodEnd, minDays, maxDays);
        return count == null ? 0L : count;
    }

    private void ensureNoContinuousPersonalLeave(String applicantNameSnapshot, LocalDate startDate, LocalDate endDate) {
        LeaveRequest adjacent = leaveRequestMapper.findFirstOverlappingOrAdjacent(
                applicantNameSnapshot, requirePersonalLeaveTypeId(), EFFECTIVE_LEAVE_STATUSES,
                startDate.minusDays(1), endDate.plusDays(1));
        if (adjacent != null) {
            throw new BizException("单次请事假2天及以上至5天以内的不得连续请休");
        }
    }

    // ==================== 其他工具方法 ====================

    public LocalDateTime currentMonthStart() {
        return LocalDate.now().withDayOfMonth(1).atStartOfDay();
    }

    public String toRoleName(String roleCode) {
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

    public String generateRequestNo() {
        return "LR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public String buildCancelComment(com.attendance.leave.dto.CancelLeaveRequestDto dto) {
        String reason = dto == null ? null : dto.getReason();
        if (reason == null || reason.isBlank()) {
            return "考勤管理员撤销请假单";
        }
        return "考勤管理员撤销请假单: " + reason.trim();
    }

    public Long requirePersonalLeaveTypeId() {
        return leaveTypeMapper.findAll().stream()
                .filter(item -> "事".equals(item.getLeaveCode()))
                .findFirst()
                .map(LeaveType::getId)
                .orElseThrow(() -> new BizException("未配置事假假别"));
    }

    public Month firstMonthOfQuarter(Month month) {
        int firstMonth = ((month.getValue() - 1) / 3) * 3 + 1;
        return Month.of(firstMonth);
    }

    public enum SelectionScenario {
        NONE,
        SECTION_LEVEL,
        SICK_WITHIN_MONTH,
        SICK_OVER_MONTH,
        PERSONAL_5_TO_10,
        PERSONAL_10_TO_30,
        PERSONAL_OVER_30
    }

    public record BatchSignaturePayload(byte[] bytes, String originalFilename) {
    }
}
