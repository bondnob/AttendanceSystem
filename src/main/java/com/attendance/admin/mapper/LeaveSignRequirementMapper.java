package com.attendance.admin.mapper;

import com.attendance.admin.model.LeaveSignRequirement;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LeaveSignRequirementMapper {

    @Insert("""
            INSERT INTO leave_sign_requirement (role_code, leave_type_id, sign_required, is_enabled)
            VALUES (#{roleCode}, #{leaveTypeId}, #{signRequired}, #{isEnabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LeaveSignRequirement requirement);

    @Update("""
            UPDATE leave_sign_requirement
            SET role_code = #{roleCode},
                leave_type_id = #{leaveTypeId},
                sign_required = #{signRequired},
                is_enabled = #{isEnabled}
            WHERE id = #{id}
            """)
    int update(LeaveSignRequirement requirement);

    @Select("""
            SELECT id, role_code, leave_type_id, sign_required, is_enabled
            FROM leave_sign_requirement
            ORDER BY id DESC
            """)
    List<LeaveSignRequirement> findAll();

    @Select("""
            SELECT id, role_code, leave_type_id, sign_required, is_enabled
            FROM leave_sign_requirement
            WHERE role_code = #{roleCode}
              AND leave_type_id = #{leaveTypeId}
              AND is_enabled = 1
            ORDER BY id DESC
            LIMIT 1
            """)
    LeaveSignRequirement findActive(String roleCode, Long leaveTypeId);
}
