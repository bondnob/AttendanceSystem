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
import com.attendance.common.PageResponse;
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
                .signatureUrl(user.getSignatureUrl())
                .token(token)
                .tokenType("Bearer")
                .expiresInSeconds(jwtTokenProvider.getExpireSeconds())
                .build();
    }

    public DashboardResponse getDashboard() {
        return DashboardResponse.builder()
                .leaveTypeRequestCounts(getDashboardLeaveTypeRequestCounts())
                .monthlyApprovalStats(getDashboardApprovalStats())
                .messages(listMyMessages(1, 5).getRecords())
                .build();
    }

    public List<DashboardLeaveTypeCountResponse> getDashboardLeaveTypeRequestCounts() {
        UserAccount currentUser = requireCurrentUser();
        Long orgUnitId = resolveDashboardOrgUnitId(currentUser);
        Long applicantId = resolveDashboardApplicantId(currentUser);
        LocalDateTime monthStart = currentMonthStart();
        LocalDateTime monthEnd = nextMonthStart(monthStart);
        return leaveRequestMapper.countMonthlyRequestsByLeaveType(monthStart, monthEnd, orgUnitId, applicantId);
    }

    public DashboardApprovalStatsResponse getDashboardApprovalStats() {
        UserAccount currentUser = requireCurrentUser();
        Long orgUnitId = resolveDashboardOrgUnitId(currentUser);
        Long applicantId = resolveDashboardApplicantId(currentUser);
        LocalDateTime monthStart = currentMonthStart();
        LocalDateTime monthEnd = nextMonthStart(monthStart);
        return buildDashboardApprovalStats(currentUser, orgUnitId, applicantId, monthStart, monthEnd);
    }

    private DashboardApprovalStatsResponse buildDashboardApprovalStats(UserAccount currentUser,
                                                                       Long orgUnitId,
                                                                       Long applicantId,
                                                                       LocalDateTime monthStart,
                                                                       LocalDateTime monthEnd) {
        if (RoleCode.ATTENDANCE_ADMIN.equals(currentUser.getRoleCode())
                || RoleCode.ORG_PRINCIPAL.equals(currentUser.getRoleCode())
                || RoleCode.WORKSHOP_PARTY_SECRETARY.equals(currentUser.getRoleCode())) {
            Long pendingCount = leaveRequestMapper.countMonthlyRequestsByStatus(
                    monthStart, monthEnd, List.of(LeaveRequestStatus.PENDING), orgUnitId, applicantId);
            Long approvedCount = countApprovedOrRejectedByScope(monthStart, monthEnd, orgUnitId, applicantId);
            return DashboardApprovalStatsResponse.builder()
                    .pendingCount(pendingCount == null ? 0L : pendingCount)
                    .approvedCount(approvedCount == null ? 0L : approvedCount)
                    .build();
        }

        Long pendingCount = leaveApprovalMapper.countMonthlyPendingForUser(
                currentUser.getId(), currentUser.getRoleCode(), currentUser.getOrgUnitId(), monthStart, monthEnd);
        Long approvedCount = leaveApprovalMapper.countMonthlyProcessedByUser(currentUser.getId(), monthStart, monthEnd);
        return DashboardApprovalStatsResponse.builder()
                .pendingCount(pendingCount == null ? 0L : pendingCount)
                .approvedCount(approvedCount == null ? 0L : approvedCount)
                .build();
    }

    private Long countApprovedOrRejectedByScope(LocalDateTime monthStart,
                                                LocalDateTime monthEnd,
                                                Long orgUnitId,
                                                Long applicantId) {
        Long approvedCount = leaveRequestMapper.countMonthlyRequestsByStatus(
                monthStart, monthEnd, List.of(LeaveRequestStatus.APPROVED), orgUnitId, applicantId);
        Long rejectedCount = leaveRequestMapper.countMonthlyRequestsByStatus(
                monthStart, monthEnd, List.of(LeaveRequestStatus.REJECTED), orgUnitId, applicantId);
        long approved = approvedCount == null ? 0L : approvedCount;
        long rejected = rejectedCount == null ? 0L : rejectedCount;
        return approved + rejected;
    }

    public PageResponse<UserMessageResponse> listMyMessages(Integer pageNum, Integer pageSize) {
        UserAccount currentUser = requireCurrentUser();
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = 5;
        int offset = (safePageNum - 1) * safePageSize;
        Long total = userMessageMapper.countByTargetUserId(currentUser.getId());
        List<UserMessageResponse> records = userMessageMapper.findPageByTargetUserId(currentUser.getId(), offset, safePageSize).stream()
                .map(this::toUserMessageResponse)
                .toList();
        return PageResponse.<UserMessageResponse>builder()
                .total(total == null ? 0L : total)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .records(records)
                .build();
    }

    private Long resolveDashboardOrgUnitId(UserAccount currentUser) {
        if ("ORG".equals(currentUser.getDataScope())) {
            return currentUser.getOrgUnitId();
        }
        return null;
    }

    private Long resolveDashboardApplicantId(UserAccount currentUser) {
        if (!"ORG".equals(currentUser.getDataScope()) && !"ALL".equals(currentUser.getDataScope())) {
            return currentUser.getId();
        }
        return null;
    }

    private LocalDateTime currentMonthStart() {
        return LocalDate.now().withDayOfMonth(1).atStartOfDay();
    }

    private LocalDateTime nextMonthStart(LocalDateTime monthStart) {
        return monthStart.plusMonths(1);
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
