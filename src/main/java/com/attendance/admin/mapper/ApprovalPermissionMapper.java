package com.attendance.admin.mapper;

import com.attendance.admin.model.ApprovalPermission;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApprovalPermissionMapper {

    @Insert("""
            INSERT INTO approval_permission
            (org_unit_id, role_code, applicant_type, position_level_code, leave_scope, min_days, max_days, is_enabled)
            VALUES
            (#{orgUnitId}, #{roleCode}, #{applicantType}, #{positionLevelCode}, #{leaveScope}, #{minDays}, #{maxDays}, #{isEnabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ApprovalPermission permission);

    @Update("""
            UPDATE approval_permission
            SET org_unit_id = #{orgUnitId},
                role_code = #{roleCode},
                applicant_type = #{applicantType},
                position_level_code = #{positionLevelCode},
                leave_scope = #{leaveScope},
                min_days = #{minDays},
                max_days = #{maxDays},
                is_enabled = #{isEnabled}
            WHERE id = #{id}
            """)
    int update(ApprovalPermission permission);

    @Select("""
            SELECT id, org_unit_id, role_code, applicant_type, position_level_code, leave_scope, min_days, max_days, is_enabled
            FROM approval_permission
            ORDER BY id DESC
            """)
    List<ApprovalPermission> findAll();

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM approval_permission
            <where>
                <if test="orgUnitId != null">
                    org_unit_id = #{orgUnitId}
                </if>
                <if test="leaveScope != null and leaveScope != ''">
                    AND leave_scope = #{leaveScope}
                </if>
            </where>
            </script>
            """)
    Long countByCondition(@Param("orgUnitId") Long orgUnitId, @Param("leaveScope") String leaveScope);

    @Select("""
            <script>
            SELECT id, org_unit_id, role_code, applicant_type, position_level_code, leave_scope, min_days, max_days, is_enabled
            FROM approval_permission
            <where>
                <if test="orgUnitId != null">
                    org_unit_id = #{orgUnitId}
                </if>
                <if test="leaveScope != null and leaveScope != ''">
                    AND leave_scope = #{leaveScope}
                </if>
            </where>
            ORDER BY id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<ApprovalPermission> findPageByCondition(@Param("orgUnitId") Long orgUnitId,
                                                 @Param("leaveScope") String leaveScope,
                                                 @Param("offset") Integer offset,
                                                 @Param("pageSize") Integer pageSize);

    @Select("""
            SELECT id, org_unit_id, role_code, applicant_type, position_level_code, leave_scope, min_days, max_days, is_enabled
            FROM approval_permission
            WHERE id = #{id}
            """)
    ApprovalPermission findById(@Param("id") Long id);

    @Select("""
            SELECT id, org_unit_id, role_code, applicant_type, position_level_code, leave_scope, min_days, max_days, is_enabled
            FROM approval_permission
            WHERE org_unit_id = #{orgUnitId}
              AND role_code = #{roleCode}
              AND is_enabled = 1
            ORDER BY id DESC
            """)
    List<ApprovalPermission> findEnabledByOrgAndRole(@Param("orgUnitId") Long orgUnitId,
                                                     @Param("roleCode") String roleCode);

    @Update("""
            UPDATE approval_permission
            SET is_enabled = #{isEnabled}
            WHERE id = #{id}
            """)
    int updateEnabled(@Param("id") Long id, @Param("isEnabled") Integer isEnabled);
}
