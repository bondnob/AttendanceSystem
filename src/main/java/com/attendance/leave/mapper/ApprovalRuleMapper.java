package com.attendance.leave.mapper;

import com.attendance.leave.model.ApprovalRule;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApprovalRuleMapper {

    @Select("""
            SELECT id, rule_code, rule_name, applicant_type, position_level_code, leave_scope,
                   exceeds_month_only, min_days, max_days, description, is_enabled
            FROM approval_rule
            WHERE applicant_type = #{applicantType}
              AND position_level_code = #{positionLevelCode}
              AND is_enabled = 1
            ORDER BY exceeds_month_only ASC, id ASC
            """)
    List<ApprovalRule> findActiveRules(@Param("applicantType") String applicantType,
                                       @Param("positionLevelCode") String positionLevelCode);
}
