package com.attendance.leave.mapper;

import com.attendance.leave.model.ApprovalRuleStep;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApprovalRuleStepMapper {

    @Select("""
            SELECT id, rule_id, step_no, action_type, assignee_count, candidate_group, step_code, step_code_name, step_name, approver_source,
                   approver_role_code, approver_role_name, return_to_org
            FROM approval_rule_step
            WHERE rule_id = #{ruleId}
            ORDER BY step_no ASC
            """)
    List<ApprovalRuleStep> findByRuleId(@Param("ruleId") Long ruleId);
}
