package com.attendance.leave.mapper;

import com.attendance.leave.model.UserAccount;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserAccountMapper {

    @Select("""
            SELECT id, username, password_hash, role_code, role_name, emp_name, id_card_no, team_name, work_type,
                     applicant_type, position_level_code, leader_group_code, org_unit_id, data_scope, approval_scope, is_enabled
            FROM user_account
            WHERE id = #{id}
            """)
    UserAccount findById(@Param("id") Long id);

    @Select("""
            SELECT id, username, password_hash, role_code, role_name, emp_name, id_card_no, team_name, work_type,
                    applicant_type, position_level_code, leader_group_code, org_unit_id, data_scope, approval_scope, is_enabled
            FROM user_account
            WHERE username = #{username}
            """)
    UserAccount findByUsername(@Param("username") String username);

    @Select("""
            SELECT id, username, password_hash, role_code, role_name, emp_name, id_card_no, team_name, work_type,
                    applicant_type, position_level_code, leader_group_code, org_unit_id, data_scope, approval_scope, is_enabled
            FROM user_account
            WHERE emp_name = #{empName}
              AND is_enabled = 1
            ORDER BY id ASC
            """)
    List<UserAccount> findEnabledByEmpName(@Param("empName") String empName);

    @Select("""
            SELECT id, username, password_hash, role_code, role_name, emp_name, id_card_no, team_name, work_type,
                    applicant_type, position_level_code, leader_group_code, org_unit_id, data_scope, approval_scope, is_enabled
            FROM user_account
            WHERE org_unit_id = #{orgUnitId} AND role_code = #{roleCode}
            LIMIT 1
            """)
    UserAccount findByOrgAndRole(@Param("orgUnitId") Long orgUnitId, @Param("roleCode") String roleCode);

    @Select("""
            SELECT id, username, password_hash, role_code, role_name, emp_name, id_card_no, team_name, work_type,
                     applicant_type, position_level_code, leader_group_code, org_unit_id, data_scope, approval_scope, is_enabled
            FROM user_account
            WHERE role_code = #{roleCode}
            LIMIT 1
            """)
    UserAccount findByRole(@Param("roleCode") String roleCode);

    @Select("""
            SELECT id, username, password_hash, role_code, role_name, emp_name, id_card_no, team_name, work_type,
                     applicant_type, position_level_code, leader_group_code, org_unit_id, data_scope, approval_scope, is_enabled
            FROM user_account
            WHERE leader_group_code = #{leaderGroupCode}
              AND is_enabled = 1
            ORDER BY id ASC
            """)
    List<UserAccount> findByLeaderGroup(@Param("leaderGroupCode") String leaderGroupCode);

    @Select("""
            SELECT id, username, password_hash, role_code, role_name, emp_name, id_card_no, team_name, work_type,
                     applicant_type, position_level_code, leader_group_code, org_unit_id, data_scope, approval_scope, is_enabled
            FROM user_account
            ORDER BY id DESC
            """)
    List<UserAccount> findAll();

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM user_account
            <if test="empName != null and empName != ''">
                WHERE emp_name LIKE CONCAT('%', #{empName}, '%')
            </if>
            </script>
            """)
    Long countByEmpName(@Param("empName") String empName);

    @Select("""
            <script>
            SELECT id, username, password_hash, role_code, role_name, emp_name, id_card_no, team_name, work_type,
                     applicant_type, position_level_code, leader_group_code, org_unit_id, data_scope, approval_scope, is_enabled
            FROM user_account
            <if test="empName != null and empName != ''">
                WHERE emp_name LIKE CONCAT('%', #{empName}, '%')
            </if>
            ORDER BY id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<UserAccount> findPageByEmpName(@Param("empName") String empName,
                                        @Param("offset") Integer offset,
                                        @Param("pageSize") Integer pageSize);

    @Select("""
            SELECT id, username, password_hash, role_code, role_name, emp_name, id_card_no, team_name, work_type,
                     applicant_type, position_level_code, leader_group_code, org_unit_id, data_scope, approval_scope, is_enabled
            FROM user_account
            WHERE id = #{userId}
              AND is_enabled = 1
            """)
    UserAccount findEnabledById(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO user_account
            (username, password_hash, role_code, emp_name,  applicant_type,
             leader_group_code, org_unit_id, data_scope, approval_scope, is_enabled)
            VALUES
            (#{username}, #{passwordHash}, #{roleCode}, #{empName}, #{applicantType}, 
             #{leaderGroupCode}, #{orgUnitId}, #{dataScope}, #{approvalScope}, #{isEnabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserAccount userAccount);

    @Update("""
            UPDATE user_account
            SET password_hash = #{passwordHash},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    @Update("""
            UPDATE user_account
            SET role_code = #{roleCode},
                emp_name = #{empName},
                applicant_type = #{applicantType},
                leader_group_code = #{leaderGroupCode},
                org_unit_id = #{orgUnitId},
                data_scope = #{dataScope},
                approval_scope = #{approvalScope},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(UserAccount userAccount);

    @Update("""
            UPDATE user_account
            SET is_enabled = #{isEnabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateEnabled(@Param("id") Long id, @Param("isEnabled") Integer isEnabled);
}
