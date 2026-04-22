package com.attendance.leave.mapper;

import com.attendance.auth.dto.DashboardLeaveTypeCountResponse;
import com.attendance.leave.enums.LeaveRequestStatus;
import com.attendance.leave.model.LeaveRequest;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LeaveRequestMapper {

    @Insert("""
            INSERT INTO leave_request
            (request_no, applicant_id, org_unit_id, leave_type_id, approval_rule_id, applicant_name_snapshot, applicant_type, position_level_code,
             job_title_snapshot, team_leader_snapshot, start_date, end_date, start_time, end_time, leave_days, allowed_days, exceeds_one_month,
             reason, remark, status, current_step, current_action_type, submitted_by, submitted_at, created_by)
            VALUES
            (#{requestNo}, #{applicantId}, #{orgUnitId}, #{leaveTypeId}, #{approvalRuleId}, #{applicantNameSnapshot}, #{applicantType}, #{positionLevelCode},
             #{jobTitleSnapshot}, #{teamLeaderSnapshot}, #{startDate}, #{endDate}, #{startTime}, #{endTime}, #{leaveDays}, #{allowedDays}, #{exceedsOneMonth},
             #{reason}, #{remark}, #{status}, #{currentStep}, #{currentActionType}, #{submittedBy}, #{submittedAt}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LeaveRequest request);

    @Select("""
            SELECT id, request_no, applicant_id, org_unit_id, leave_type_id, approval_rule_id, applicant_type,
                   applicant_name_snapshot, position_level_code, job_title_snapshot, team_leader_snapshot, start_date, end_date, start_time, end_time, leave_days,
                   allowed_days, exceeds_one_month, reason, remark, status, current_step, current_action_type, submitted_by, submitted_at,
                   final_approved_at, created_by, created_at, updated_at
            FROM leave_request
            WHERE id = #{id}
            """)
    LeaveRequest findById(@Param("id") Long id);

    @Select({"<script>",
            "SELECT id, request_no, applicant_id, org_unit_id, leave_type_id, approval_rule_id, applicant_type,",
            "       applicant_name_snapshot, position_level_code, job_title_snapshot, team_leader_snapshot, start_date, end_date, start_time, end_time, leave_days,",
            "       allowed_days, exceeds_one_month, reason, remark, status, current_step, current_action_type, submitted_by, submitted_at,",
            "       final_approved_at, created_by, created_at, updated_at",
            "FROM leave_request",
            "<where>",
            "  <if test='orgUnitId != null'> org_unit_id = #{orgUnitId}</if>",
            "  <if test='applicantId != null'> AND applicant_id = #{applicantId}</if>",
            "  <if test='status != null and status != \"\"'> AND status = #{status}</if>",
            "</where>",
            "ORDER BY id DESC",
            "</script>"})
    List<LeaveRequest> findByScope(@Param("orgUnitId") Long orgUnitId,
                                   @Param("applicantId") Long applicantId,
                                   @Param("status") String status);

    @Select({"<script>",
            "SELECT id, request_no, applicant_id, org_unit_id, leave_type_id, approval_rule_id, applicant_type,",
            "       applicant_name_snapshot, position_level_code, job_title_snapshot, team_leader_snapshot, start_date, end_date, start_time, end_time, leave_days,",
            "       allowed_days, exceeds_one_month, reason, remark, status, current_step, current_action_type, submitted_by, submitted_at,",
            "       final_approved_at, created_by, created_at, updated_at",
            "FROM leave_request",
            "<where>",
            "  <if test='orgUnitId != null'> org_unit_id = #{orgUnitId}</if>",
            "  <if test='applicantId != null'> AND applicant_id = #{applicantId}</if>",
            "  <if test='status != null and status != \"\"'> AND status = #{status}</if>",
            "  <if test='leaveTypeId != null'> AND leave_type_id = #{leaveTypeId}</if>",
            "</where>",
            "ORDER BY id DESC",
            "LIMIT #{offset}, #{pageSize}",
            "</script>"})
    List<LeaveRequest> findPageByScope(@Param("orgUnitId") Long orgUnitId,
                                       @Param("applicantId") Long applicantId,
                                       @Param("status") String status,
                                       @Param("leaveTypeId") Long leaveTypeId,
                                       @Param("offset") Integer offset,
                                       @Param("pageSize") Integer pageSize);

    @Select({"<script>",
            "SELECT COUNT(1)",
            "FROM leave_request",
            "<where>",
            "  <if test='orgUnitId != null'> org_unit_id = #{orgUnitId}</if>",
            "  <if test='applicantId != null'> AND applicant_id = #{applicantId}</if>",
            "  <if test='status != null and status != \"\"'> AND status = #{status}</if>",
            "  <if test='leaveTypeId != null'> AND leave_type_id = #{leaveTypeId}</if>",
            "</where>",
            "</script>"})
    Long countByScope(@Param("orgUnitId") Long orgUnitId,
                      @Param("applicantId") Long applicantId,
                      @Param("status") String status,
                      @Param("leaveTypeId") Long leaveTypeId);

    @Select({"<script>",
            "SELECT COUNT(DISTINCT lr.id)",
            "FROM leave_request lr",
            "JOIN leave_approval la ON la.leave_request_id = lr.id",
            "WHERE la.approval_status = 'PENDING'",
            "  AND lr.current_step = la.step_no",
            "  AND (",
            "        la.approver_user_id = #{userId}",
            "        OR (la.approver_user_id IS NULL AND la.approver_role_code = #{roleCode}",
            "            AND (#{roleCode} &lt;&gt; 'ORG_PRINCIPAL' OR lr.org_unit_id = #{orgUnitId}))",
            "      )",
            "  <if test='status != null and status != \"\"'> AND lr.status = #{status}</if>",
            "  <if test='leaveTypeId != null'> AND lr.leave_type_id = #{leaveTypeId}</if>",
            "</script>"})
    Long countPendingForApprover(@Param("userId") Long userId,
                                 @Param("roleCode") String roleCode,
                                 @Param("orgUnitId") Long orgUnitId,
                                 @Param("status") String status,
                                 @Param("leaveTypeId") Long leaveTypeId);

    @Select({"<script>",
            "SELECT DISTINCT lr.id, lr.request_no, lr.applicant_id, lr.org_unit_id, lr.leave_type_id, lr.approval_rule_id, lr.applicant_type,",
            "       lr.applicant_name_snapshot, lr.position_level_code, lr.job_title_snapshot, lr.team_leader_snapshot, lr.start_date, lr.end_date, lr.start_time, lr.end_time, lr.leave_days,",
            "       lr.allowed_days, lr.exceeds_one_month, lr.reason, lr.remark, lr.status, lr.current_step, lr.current_action_type, lr.submitted_by, lr.submitted_at,",
            "       lr.final_approved_at, lr.created_by, lr.created_at, lr.updated_at",
            "FROM leave_request lr",
            "JOIN leave_approval la ON la.leave_request_id = lr.id",
            "WHERE la.approval_status = 'PENDING'",
            "  AND lr.current_step = la.step_no",
            "  AND (",
            "        la.approver_user_id = #{userId}",
            "        OR (la.approver_user_id IS NULL AND la.approver_role_code = #{roleCode}",
            "            AND (#{roleCode} &lt;&gt; 'ORG_PRINCIPAL' OR lr.org_unit_id = #{orgUnitId}))",
            "      )",
            "  <if test='status != null and status != \"\"'> AND lr.status = #{status}</if>",
            "  <if test='leaveTypeId != null'> AND lr.leave_type_id = #{leaveTypeId}</if>",
            "ORDER BY lr.id DESC",
            "LIMIT #{offset}, #{pageSize}",
            "</script>"})
    List<LeaveRequest> findPendingPageForApprover(@Param("userId") Long userId,
                                                  @Param("roleCode") String roleCode,
                                                  @Param("orgUnitId") Long orgUnitId,
                                                  @Param("status") String status,
                                                  @Param("leaveTypeId") Long leaveTypeId,
                                                  @Param("offset") Integer offset,
                                                  @Param("pageSize") Integer pageSize);

    @Select({"<script>",
            "SELECT COUNT(DISTINCT lr.id)",
            "FROM leave_request lr",
            "WHERE EXISTS (",
            "  SELECT 1",
            "  FROM leave_approval la",
            "  WHERE la.leave_request_id = lr.id",
            "    AND (",
            "         la.approver_user_id = #{userId}",
            "         OR (la.approver_user_id IS NULL AND la.approver_role_code = #{roleCode}",
            "             AND (#{roleCode} &lt;&gt; 'ORG_PRINCIPAL' OR lr.org_unit_id = #{orgUnitId}))",
            "    )",
            "    AND (la.approval_status IN ('APPROVED', 'REJECTED', 'SKIPPED', 'CANCELLED')",
            "         OR (la.approval_status = 'PENDING' AND lr.current_step = la.step_no))",
            ")",
            "  <if test='status != null and status != \"\"'> AND lr.status = #{status}</if>",
            "  <if test='leaveTypeId != null'> AND lr.leave_type_id = #{leaveTypeId}</if>",
            "</script>"})
    Long countByResponsibleApprover(@Param("userId") Long userId,
                                    @Param("roleCode") String roleCode,
                                    @Param("orgUnitId") Long orgUnitId,
                                    @Param("status") String status,
                                    @Param("leaveTypeId") Long leaveTypeId);

    @Select({"<script>",
            "SELECT DISTINCT lr.id, lr.request_no, lr.applicant_id, lr.org_unit_id, lr.leave_type_id, lr.approval_rule_id, lr.applicant_type,",
            "       lr.applicant_name_snapshot, lr.position_level_code, lr.job_title_snapshot, lr.team_leader_snapshot, lr.start_date, lr.end_date, lr.start_time, lr.end_time, lr.leave_days,",
            "       lr.allowed_days, lr.exceeds_one_month, lr.reason, lr.remark, lr.status, lr.current_step, lr.current_action_type, lr.submitted_by, lr.submitted_at,",
            "       lr.final_approved_at, lr.created_by, lr.created_at, lr.updated_at",
            "FROM leave_request lr",
            "WHERE EXISTS (",
            "  SELECT 1",
            "  FROM leave_approval la",
            "  WHERE la.leave_request_id = lr.id",
            "    AND (",
            "         la.approver_user_id = #{userId}",
            "         OR (la.approver_user_id IS NULL AND la.approver_role_code = #{roleCode}",
            "             AND (#{roleCode} &lt;&gt; 'ORG_PRINCIPAL' OR lr.org_unit_id = #{orgUnitId}))",
            "    )",
            "    AND (la.approval_status IN ('APPROVED', 'REJECTED', 'SKIPPED', 'CANCELLED')",
            "         OR (la.approval_status = 'PENDING' AND lr.current_step = la.step_no))",
            ")",
            "  <if test='status != null and status != \"\"'> AND lr.status = #{status}</if>",
            "  <if test='leaveTypeId != null'> AND lr.leave_type_id = #{leaveTypeId}</if>",
            "ORDER BY lr.id DESC",
            "LIMIT #{offset}, #{pageSize}",
            "</script>"})
    List<LeaveRequest> findPageByResponsibleApprover(@Param("userId") Long userId,
                                                     @Param("roleCode") String roleCode,
                                                     @Param("orgUnitId") Long orgUnitId,
                                                     @Param("status") String status,
                                                     @Param("leaveTypeId") Long leaveTypeId,
                                                     @Param("offset") Integer offset,
                                                     @Param("pageSize") Integer pageSize);

    @Select({"<script>",
            "SELECT lr.leave_type_id AS leaveTypeId, lt.leave_name AS leaveTypeName, COUNT(1) AS requestCount",
            "FROM leave_request lr",
            "JOIN leave_type lt ON lt.id = lr.leave_type_id",
            "<where>",
            "  <if test='orgUnitId != null'> lr.org_unit_id = #{orgUnitId}</if>",
            "  <if test='applicantId != null'> AND lr.applicant_id = #{applicantId}</if>",
            "</where>",
            "GROUP BY lr.leave_type_id, lt.leave_name",
            "ORDER BY lr.leave_type_id ASC",
            "</script>"})
    List<DashboardLeaveTypeCountResponse> countRequestsByLeaveType(@Param("orgUnitId") Long orgUnitId,
                                                                   @Param("applicantId") Long applicantId);

    @Select({"<script>",
            "SELECT COUNT(1)",
            "FROM leave_request",
            "<where>",
            "  submitted_at &gt;= #{monthStart}",
            "  AND submitted_at &lt; #{monthEnd}",
            "  AND status IN",
            "  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>",
            "    #{status}",
            "  </foreach>",
            "  <if test='orgUnitId != null'> AND org_unit_id = #{orgUnitId}</if>",
            "  <if test='applicantId != null'> AND applicant_id = #{applicantId}</if>",
            "</where>",
            "</script>"})
    Long countMonthlyRequestsByStatus(@Param("monthStart") java.time.LocalDateTime monthStart,
                                      @Param("monthEnd") java.time.LocalDateTime monthEnd,
                                      @Param("statuses") List<String> statuses,
                                      @Param("orgUnitId") Long orgUnitId,
                                      @Param("applicantId") Long applicantId);

    @Select({"<script>",
            "SELECT COUNT(1)",
            "FROM leave_request",
            "WHERE applicant_id = #{applicantId}",
            "  AND leave_type_id = #{leaveTypeId}",
            "  AND status IN",
            "  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>",
            "    #{status}",
            "  </foreach>",
            "  AND start_date &gt;= #{periodStart}",
            "  AND start_date &lt;= #{periodEnd}",
            "  <if test='minDays != null'> AND leave_days &gt;= #{minDays}</if>",
            "  <if test='maxDays != null'> AND leave_days &lt;= #{maxDays}</if>",
            "</script>"})
    Long countLeaveRequestsByApplicantAndRange(@Param("applicantId") Long applicantId,
                                               @Param("leaveTypeId") Long leaveTypeId,
                                               @Param("statuses") List<String> statuses,
                                               @Param("periodStart") java.time.LocalDate periodStart,
                                               @Param("periodEnd") java.time.LocalDate periodEnd,
                                               @Param("minDays") java.math.BigDecimal minDays,
                                               @Param("maxDays") java.math.BigDecimal maxDays);

    @Select({"<script>",
            "SELECT id, request_no, applicant_id, org_unit_id, leave_type_id, approval_rule_id, applicant_type,",
            "       applicant_name_snapshot, position_level_code, job_title_snapshot, team_leader_snapshot, start_date, end_date, start_time, end_time, leave_days,",
            "       allowed_days, exceeds_one_month, reason, remark, status, current_step, current_action_type, submitted_by, submitted_at,",
            "       final_approved_at, created_by, created_at, updated_at",
            "FROM leave_request",
            "WHERE applicant_id = #{applicantId}",
            "  AND leave_type_id = #{leaveTypeId}",
            "  AND status IN",
            "  <foreach collection='statuses' item='status' open='(' separator=',' close=')'>",
            "    #{status}",
            "  </foreach>",
            "  AND start_date &lt;= #{endDate}",
            "  AND end_date &gt;= #{startDate}",
            "ORDER BY start_date DESC, id DESC",
            "LIMIT 1",
            "</script>"})
    LeaveRequest findFirstOverlappingOrAdjacent(@Param("applicantId") Long applicantId,
                                                @Param("leaveTypeId") Long leaveTypeId,
                                                @Param("statuses") List<String> statuses,
                                                @Param("startDate") java.time.LocalDate startDate,
                                                @Param("endDate") java.time.LocalDate endDate);

    @Update("""
            UPDATE leave_request
            SET status = #{status},
                current_step = #{currentStep},
                current_action_type = #{currentActionType},
                final_approved_at = #{finalApprovedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateApprovalState(LeaveRequest request);
}
