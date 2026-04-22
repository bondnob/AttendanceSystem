package com.attendance.leave.mapper;

import com.attendance.leave.model.LeaveApproval;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;

@Mapper
public interface LeaveApprovalMapper {

    @Insert("""
            INSERT INTO leave_approval
            (leave_request_id, rule_step_id, step_no, action_type, step_name, approver_role_code, approver_user_id, approval_status, approval_comment, signature_url)
            VALUES
            (#{leaveRequestId}, #{ruleStepId}, #{stepNo}, #{actionType}, #{stepName}, #{approverRoleCode}, #{approverUserId}, #{approvalStatus}, #{approvalComment}, #{signatureUrl})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LeaveApproval approval);

    @Select("""
            SELECT id, leave_request_id, rule_step_id, step_no, action_type, step_name, approver_role_code, approver_user_id,
                   approval_status, approval_comment, signature_url, approved_at, created_at, updated_at
            FROM leave_approval
            WHERE leave_request_id = #{leaveRequestId}
            ORDER BY step_no ASC, id ASC
            """)
    List<LeaveApproval> findByLeaveRequestId(@Param("leaveRequestId") Long leaveRequestId);

    @Select("""
            SELECT id, leave_request_id, rule_step_id, step_no, action_type, step_name, approver_role_code, approver_user_id,
                   approval_status, approval_comment, signature_url, approved_at, created_at, updated_at
            FROM leave_approval
            WHERE leave_request_id = #{leaveRequestId}
              AND step_no = #{stepNo}
              AND approval_status = 'PENDING'
            LIMIT 1
            """)
    LeaveApproval findPendingByStep(@Param("leaveRequestId") Long leaveRequestId, @Param("stepNo") Integer stepNo);

    @Select("""
            SELECT id, leave_request_id, rule_step_id, step_no, action_type, step_name, approver_role_code, approver_user_id,
                   approval_status, approval_comment, signature_url, approved_at, created_at, updated_at
            FROM leave_approval
            WHERE leave_request_id = #{leaveRequestId}
              AND approval_status = 'PENDING'
            ORDER BY step_no ASC
            LIMIT 1
            """)
    LeaveApproval findFirstPending(@Param("leaveRequestId") Long leaveRequestId);

    @Update("""
            UPDATE leave_approval
            SET approver_user_id = #{approverUserId},
                approval_status = #{approvalStatus},
                approval_comment = #{approvalComment},
                signature_url = #{signatureUrl},
                approved_at = #{approvedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateDecision(LeaveApproval approval);

    @Update("""
            UPDATE leave_approval
            SET approver_user_id = #{approverUserId},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateApprover(LeaveApproval approval);

    @Update("""
            UPDATE leave_approval
            SET approval_status = 'CANCELLED',
                approval_comment = #{comment},
                approved_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE leave_request_id = #{leaveRequestId}
              AND approval_status = 'PENDING'
            """)
    int cancelPendingByLeaveRequestId(@Param("leaveRequestId") Long leaveRequestId,
                                      @Param("comment") String comment);

    @Select("""
            SELECT COUNT(1)
            FROM leave_approval la
            JOIN leave_request lr ON lr.id = la.leave_request_id
            WHERE la.approval_status = 'PENDING'
              AND (
                    la.approver_user_id = #{userId}
                    OR (la.approver_user_id IS NULL AND la.approver_role_code = #{roleCode}
                        AND (#{roleCode} <> 'ORG_PRINCIPAL' OR lr.org_unit_id = #{orgUnitId}))
                  )
            """)
    Long countPendingForUser(@Param("userId") Long userId,
                             @Param("roleCode") String roleCode,
                             @Param("orgUnitId") Long orgUnitId);

    @Select("""
            SELECT COUNT(1)
            FROM leave_approval
            WHERE approver_user_id = #{userId}
              AND approval_status IN ('APPROVED', 'REJECTED')
            """)
    Long countProcessedByUser(@Param("userId") Long userId);
}
