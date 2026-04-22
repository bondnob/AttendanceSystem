package com.attendance.admin.service;

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
import com.attendance.admin.mapper.ApprovalPermissionMapper;
import com.attendance.admin.mapper.LeaveSignRequirementMapper;
import com.attendance.admin.mapper.OrgUnitMapper;
import com.attendance.admin.model.ApprovalPermission;
import com.attendance.admin.model.LeaveSignRequirement;
import com.attendance.admin.model.OrgUnit;
import com.attendance.auth.mapper.UserMessageMapper;
import com.attendance.auth.model.UserMessage;
import com.attendance.auth.security.CurrentUser;
import com.attendance.auth.security.UserContext;
import com.attendance.common.PageResponse;
import com.attendance.common.PasswordUtils;
import com.attendance.exception.BizException;
import com.attendance.leave.mapper.UserAccountMapper;
import com.attendance.leave.model.UserAccount;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String ORG_TYPE_DEPARTMENT = "DEPARTMENT";
    private static final String ORG_TYPE_WORKSHOP = "WORKSHOP";

    private final OrgUnitMapper orgUnitMapper;
    private final UserAccountMapper userAccountMapper;
    private final ApprovalPermissionMapper approvalPermissionMapper;
    private final LeaveSignRequirementMapper leaveSignRequirementMapper;
    private final UserMessageMapper userMessageMapper;

    @Transactional
    public OrgUnitResponse createOrgUnit(CreateOrgUnitRequest request) {
        requireSystemAdmin();
        OrgUnit orgUnit = new OrgUnit();
        String orgType = normalizeOrgType(request.getOrgType());
        orgUnit.setOrgCode(generateOrgCode(orgType));
        orgUnit.setOrgName(request.getOrgName());
        orgUnit.setOrgType(orgType);
        orgUnit.setSortNo(generateSortNo());
        orgUnit.setIsEnabled(1);
        orgUnitMapper.insert(orgUnit);
        return toOrgUnitResponse(orgUnit);
    }

    @Transactional
    public UserSummaryResponse createUser(CreateUserRequest request) {
        requireSystemAdmin();
        UserAccount user = new UserAccount();
        user.setUsername(request.getUsername());
        user.setPasswordHash(PasswordUtils.encode(request.getPassword()));
        user.setRoleCode(request.getRoleCode());
        user.setEmpName(request.getEmpName());
        user.setApplicantType(request.getApplicantType());
        user.setLeaderGroupCode(request.getLeaderGroupCode());
        user.setOrgUnitId(request.getOrgUnitId());
        user.setDataScope(request.getDataScope());
        user.setApprovalScope(request.getApprovalScope());
        user.setIsEnabled(1);
        userAccountMapper.insert(user);
        return toUserSummary(user);
    }

    @Transactional
    public OrgUnitResponse updateOrgUnit(Long orgUnitId, UpdateOrgUnitRequest request) {
        requireSystemAdmin();
        OrgUnit orgUnit = requireOrgUnit(orgUnitId);
        orgUnit.setOrgCode(generateOrgCode(request.getOrgType()));
        orgUnit.setOrgType(request.getOrgType());
        orgUnit.setSortNo(request.getSortNo());
        orgUnit.setIsEnabled(request.getIsEnabled());

        orgUnitMapper.update(orgUnit);
        return toOrgUnitResponse(orgUnit);
    }

    @Transactional
    public void updateOrgUnitEnabled(Long orgUnitId, UpdateEnabledRequest request) {
        requireSystemAdmin();
        requireOrgUnit(orgUnitId);
        orgUnitMapper.updateEnabled(orgUnitId, request.getIsEnabled());
    }

    @Transactional
    public UserSummaryResponse updateUser(Long userId, UpdateUserRequest request) {
        requireSystemAdmin();
        UserAccount user = requireUser(userId);
        user.setRoleCode(request.getRoleCode());
        user.setEmpName(request.getEmpName());
        user.setApplicantType(request.getApplicantType());
        user.setLeaderGroupCode(request.getLeaderGroupCode());
        user.setOrgUnitId(request.getOrgUnitId());
        user.setDataScope(request.getDataScope());
        user.setApprovalScope(request.getApprovalScope());
        user.setIsEnabled(request.getIsEnabled());
        userAccountMapper.update(user);
        return toUserSummary(user);
    }

    @Transactional
    public void updateUserEnabled(Long userId, UpdateEnabledRequest request) {
        CurrentUser currentUser = requireSystemAdmin();
        if (currentUser.getUserId().equals(userId)) {
            throw new BizException("不能停用当前超级管理员账号");
        }
        requireUser(userId);
        userAccountMapper.updateEnabled(userId, request.getIsEnabled());
    }

    @Transactional
    public void resetPassword(Long userId, ResetPasswordRequest request) {
        requireSystemAdmin();
        if (userAccountMapper.findById(userId) == null) {
            throw new BizException("用户不存在");
        }
        userAccountMapper.updatePassword(userId, PasswordUtils.encode(request.getNewPassword()));
    }

    @Transactional
    public void saveApprovalPermission(SaveApprovalPermissionRequest request) {
        requireSystemAdmin();
        ApprovalPermission permission = new ApprovalPermission();
        permission.setId(request.getId());
        permission.setOrgUnitId(request.getOrgUnitId());
        permission.setRoleCode(request.getRoleCode());
        permission.setApplicantType(request.getApplicantType());
        permission.setPositionLevelCode(request.getPositionLevelCode());
        permission.setLeaveScope(request.getLeaveScope());
        permission.setMinDays(request.getMinDays());
        permission.setMaxDays(request.getMaxDays());
        permission.setIsEnabled(request.getIsEnabled() == null ? 1 : request.getIsEnabled());
        if (request.getId() == null) {
            approvalPermissionMapper.insert(permission);
        } else {
            approvalPermissionMapper.update(permission);
        }
    }

    public PageResponse<OrgUnitResponse> listOrgUnits(String orgName, Integer pageNum, Integer pageSize) {
        requireSystemAdmin();
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int offset = (safePageNum - 1) * safePageSize;
        String keyword = normalizeKeyword(orgName);
        Long total = orgUnitMapper.countByOrgName(keyword);
        List<OrgUnitResponse> data = orgUnitMapper.findPageByOrgName(keyword, offset, safePageSize).stream()
                .map(this::toOrgUnitResponse)
                .collect(Collectors.toList());
        return PageResponse.<OrgUnitResponse>builder()
                .total(total == null ? 0L : total)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .records(data)
                .build();
    }

    public PageResponse<UserSummaryResponse> listUsers(String empName, Integer pageNum, Integer pageSize) {
        requireSystemAdmin();
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int offset = (safePageNum - 1) * safePageSize;
        String keyword = normalizeKeyword(empName);
        Long total = userAccountMapper.countByEmpName(keyword);
        List<UserSummaryResponse> data = userAccountMapper.findPageByEmpName(keyword, offset, safePageSize).stream()
                .map(this::toUserSummary)
                .collect(Collectors.toList());
        return PageResponse.<UserSummaryResponse>builder()
                .total(total == null ? 0L : total)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .records(data)
                .build();
    }

    public PageResponse<ApprovalPermission> listApprovalPermissions(Long orgUnitId, String leaveScope,
                                                                    Integer pageNum, Integer pageSize) {
        requireSystemAdmin();
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int offset = (safePageNum - 1) * safePageSize;
        Long total = approvalPermissionMapper.countByCondition(orgUnitId, leaveScope);
        List<ApprovalPermission> data = approvalPermissionMapper.findPageByCondition(
                orgUnitId, leaveScope, offset, safePageSize);
        return PageResponse.<ApprovalPermission>builder()
                .total(total == null ? 0L : total)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .records(data)
                .build();
    }

    @Transactional
    public void updateApprovalPermissionEnabled(Long permissionId, UpdateEnabledRequest request) {
        requireSystemAdmin();
        if (approvalPermissionMapper.findById(permissionId) == null) {
            throw new BizException("审批权限不存在");
        }
        approvalPermissionMapper.updateEnabled(permissionId, request.getIsEnabled());
    }

    @Transactional
    public void saveLeaveSignRequirement(SaveLeaveSignRequirementRequest request) {
        requireSystemAdmin();
        LeaveSignRequirement requirement = new LeaveSignRequirement();
        requirement.setId(request.getId());
        requirement.setRoleCode(request.getRoleCode());
        requirement.setLeaveTypeId(request.getLeaveTypeId());
        requirement.setSignRequired(request.getSignRequired());
        requirement.setIsEnabled(request.getIsEnabled() == null ? 1 : request.getIsEnabled());
        if (request.getId() == null) {
            leaveSignRequirementMapper.insert(requirement);
        } else {
            leaveSignRequirementMapper.update(requirement);
        }
    }

    public List<LeaveSignRequirement> listLeaveSignRequirements() {
        requireSystemAdmin();
        return leaveSignRequirementMapper.findAll();
    }

    @Transactional
    public void sendUserMessage(SendUserMessageRequest request) {
        CurrentUser currentUser = requireSystemAdmin();
        UserAccount sender = userAccountMapper.findById(currentUser.getUserId());
        for (Long targetUserId : request.getTargetUserIds()) {
            if (currentUser.getUserId().equals(targetUserId)) {
                throw new BizException("不能给当前超级管理员自己发送提示");
            }
            UserAccount targetUser = userAccountMapper.findEnabledById(targetUserId);
            if (targetUser == null) {
                throw new BizException("接收账号不存在或已停用: " + targetUserId);
            }
            UserMessage message = new UserMessage();
            message.setSenderUserId(sender.getId());
            message.setTargetUserId(targetUserId);
            message.setTitle(request.getTitle().trim());
            message.setContent(request.getContent().trim());
            userMessageMapper.insert(message);
        }
    }

    private UserSummaryResponse toUserSummary(UserAccount user) {
        return UserSummaryResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .roleCode(user.getRoleCode())
                .roleName(user.getRoleName())
                .empName(user.getEmpName())
                .orgUnitId(user.getOrgUnitId())
                .jobTitle(user.getJobTitle())
                .applicantType(user.getApplicantType())
                .positionLevelCode(user.getPositionLevelCode())
                .leaderGroupCode(user.getLeaderGroupCode())
                .dataScope(user.getDataScope())
                .approvalScope(user.getApprovalScope())
                .isEnabled(user.getIsEnabled())
                .build();
    }

    private OrgUnitResponse toOrgUnitResponse(OrgUnit orgUnit) {
        return OrgUnitResponse.builder()
                .id(orgUnit.getId())
                .orgCode(orgUnit.getOrgCode())
                .orgName(orgUnit.getOrgName())
                .orgType(orgUnit.getOrgType())
                .sortNo(orgUnit.getSortNo())
                .isEnabled(orgUnit.getIsEnabled())
                .build();
    }

    private String normalizeOrgType(String orgType) {
        if (ORG_TYPE_DEPARTMENT.equals(orgType) || ORG_TYPE_WORKSHOP.equals(orgType)) {
            return orgType;
        }
        throw new BizException("组织类型不存在");
    }

    private String generateOrgCode(String orgType) {
        String prefix = switch (orgType) {
            case ORG_TYPE_DEPARTMENT -> "D";
            case ORG_TYPE_WORKSHOP -> "W";
            default -> throw new BizException("组织类型不存在");
        };
        String latestOrgCode = orgUnitMapper.findLatestOrgCodeByType(orgType);
        int nextNumber = 1;
        if (latestOrgCode != null && latestOrgCode.length() > 1) {
            try {
                nextNumber = Integer.parseInt(latestOrgCode.substring(1)) + 1;
            } catch (NumberFormatException ex) {
                throw new BizException("组织编码格式错误，无法自动生成");
            }
        }
        return prefix + String.format("%02d", nextNumber);
    }

    private Integer generateSortNo() {
        Integer maxSortNo = orgUnitMapper.findMaxSortNo();
        return maxSortNo == null ? 1 : maxSortNo + 1;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserAccount requireUser(Long userId) {
        UserAccount user = userAccountMapper.findById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private OrgUnit requireOrgUnit(Long orgUnitId) {
        OrgUnit orgUnit = orgUnitMapper.findById(orgUnitId);
        if (orgUnit == null) {
            throw new BizException("组织不存在");
        }
        return orgUnit;
    }

    private CurrentUser requireSystemAdmin() {
        CurrentUser currentUser = UserContext.get();
        if (currentUser == null || !SYSTEM_ADMIN.equals(currentUser.getRoleCode())) {
            throw new BizException("只有超级管理员可以执行该操作");
        }
        return currentUser;
    }
}
