package com.attendance.auth.service;

import com.attendance.auth.dto.DashboardApprovalStatsResponse;
import com.attendance.auth.dto.DashboardLeaveTypeCountResponse;
import com.attendance.auth.dto.DashboardResponse;
import com.attendance.auth.dto.LoginRequest;
import com.attendance.auth.dto.LoginResponse;
import com.attendance.auth.dto.UserMessageResponse;
import com.attendance.auth.mapper.UserMessageMapper;
import com.attendance.auth.model.UserMessage;
import com.attendance.auth.security.CurrentUser;
import com.attendance.auth.security.JwtTokenProvider;
import com.attendance.auth.security.UserContext;
import com.attendance.common.PasswordUtils;
import com.attendance.exception.BizException;
import com.attendance.leave.enums.LeaveRequestStatus;
import com.attendance.leave.enums.RoleCode;
import com.attendance.leave.mapper.LeaveApprovalMapper;
import com.attendance.leave.mapper.LeaveRequestMapper;
import com.attendance.leave.mapper.UserAccountMapper;
import com.attendance.leave.model.UserAccount;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveApprovalMapper leaveApprovalMapper;
    private final UserMessageMapper userMessageMapper;

    public LoginResponse login(LoginRequest request) {
        UserAccount user = userAccountMapper.findByUsername(request.getUsername());
        if (user == null) {
            log.warn("Login failed, username not found: {}", request.getUsername());
            throw new BizException("账号不存在");
        }
        if (!PasswordUtils.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed, password mismatch: {}", request.getUsername());
            throw new BizException("密码错误");
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRoleCode(), user.getOrgUnitId());
        log.info("Login success, userId={}, username={}, roleCode={}", user.getId(), user.getUsername(), user.getRoleCode());
        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .roleCode(user.getRoleCode())
                .empName(user.getEmpName())
                .orgUnitId(user.getOrgUnitId())
                .dataScope(user.getDataScope())
                .approvalScope(user.getApprovalScope())
                .token(token)
                .tokenType("Bearer")
                .expiresInSeconds(jwtTokenProvider.getExpireSeconds())
                .build();
    }

    public DashboardResponse getDashboard() {
        UserAccount currentUser = requireCurrentUser();
        Long orgUnitId = null;
        Long applicantId = null;
        if ("ORG".equals(currentUser.getDataScope())) {
            orgUnitId = currentUser.getOrgUnitId();
        } else if (!"ALL".equals(currentUser.getDataScope())) {
            applicantId = currentUser.getId();
        }

        List<DashboardLeaveTypeCountResponse> leaveTypeRequestCounts =
                leaveRequestMapper.countRequestsByLeaveType(orgUnitId, applicantId);
        DashboardApprovalStatsResponse approvalStats = buildDashboardApprovalStats(currentUser, orgUnitId, applicantId);

        return DashboardResponse.builder()
                .leaveTypeRequestCounts(leaveTypeRequestCounts)
                .monthlyApprovalStats(approvalStats)
                .messages(listMyMessages())
                .build();
    }

    private DashboardApprovalStatsResponse buildDashboardApprovalStats(UserAccount currentUser, Long orgUnitId, Long applicantId) {
        if (RoleCode.ATTENDANCE_ADMIN.equals(currentUser.getRoleCode())
                || RoleCode.ORG_PRINCIPAL.equals(currentUser.getRoleCode())) {
            Long pendingCount = leaveRequestMapper.countByScope(orgUnitId, applicantId, LeaveRequestStatus.PENDING, null);
            Long approvedCount = leaveRequestMapper.countByScope(
                    orgUnitId, applicantId, null, null);
            approvedCount = countApprovedOrRejectedByScope(orgUnitId, applicantId);
            return DashboardApprovalStatsResponse.builder()
                    .pendingCount(pendingCount == null ? 0L : pendingCount)
                    .approvedCount(approvedCount == null ? 0L : approvedCount)
                    .build();
        }

        Long pendingCount = leaveApprovalMapper.countPendingForUser(
                currentUser.getId(), currentUser.getRoleCode(), currentUser.getOrgUnitId());
        Long approvedCount = leaveApprovalMapper.countProcessedByUser(currentUser.getId());
        return DashboardApprovalStatsResponse.builder()
                .pendingCount(pendingCount == null ? 0L : pendingCount)
                .approvedCount(approvedCount == null ? 0L : approvedCount)
                .build();
    }

    private Long countApprovedOrRejectedByScope(Long orgUnitId, Long applicantId) {
        Long approvedCount = leaveRequestMapper.countByScope(orgUnitId, applicantId, LeaveRequestStatus.APPROVED, null);
        Long rejectedCount = leaveRequestMapper.countByScope(orgUnitId, applicantId, LeaveRequestStatus.REJECTED, null);
        long approved = approvedCount == null ? 0L : approvedCount;
        long rejected = rejectedCount == null ? 0L : rejectedCount;
        return approved + rejected;
    }

    public List<UserMessageResponse> listMyMessages() {
        UserAccount currentUser = requireCurrentUser();
        return userMessageMapper.findRecentByTargetUserId(currentUser.getId(), 20).stream()
                .map(this::toUserMessageResponse)
                .toList();
    }

    private UserMessageResponse toUserMessageResponse(UserMessage message) {
        UserAccount sender = userAccountMapper.findById(message.getSenderUserId());
        return UserMessageResponse.builder()
                .id(message.getId())
                .title(message.getTitle())
                .content(message.getContent())
                .senderUserId(message.getSenderUserId())
                .senderName(sender == null ? null : sender.getEmpName())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private UserAccount requireCurrentUser() {
        CurrentUser currentUser = UserContext.get();
        if (currentUser == null || currentUser.getUserId() == null) {
            throw new BizException("未登录或 token 已失效");
        }
        UserAccount user = userAccountMapper.findById(currentUser.getUserId());
        if (user == null) {
            throw new BizException("当前用户不存在");
        }
        return user;
    }
}
