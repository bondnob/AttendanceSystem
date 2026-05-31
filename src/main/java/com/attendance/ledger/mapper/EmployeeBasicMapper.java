package com.attendance.ledger.mapper;

import com.attendance.ledger.model.EmployeeBasic;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EmployeeBasicMapper {

    @Insert("""
            INSERT INTO employee_basic (id_card_no, emp_name, gender, birth_date, age, work_type,
                identity_type, category_major, category_minor, labor_shift, is_team_leader,
                org_unit_id, team_name, is_active, upload_batch)
            VALUES (#{idCardNo}, #{empName}, #{gender}, #{birthDate}, #{age}, #{workType},
                #{identityType}, #{categoryMajor}, #{categoryMinor}, #{laborShift}, #{isTeamLeader},
                #{orgUnitId}, #{teamName}, #{isActive}, #{uploadBatch})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EmployeeBasic employee);

    @Update("""
            UPDATE employee_basic
            SET emp_name = #{empName}, gender = #{gender}, birth_date = #{birthDate}, age = #{age},
                work_type = #{workType}, identity_type = #{identityType},
                category_major = #{categoryMajor}, category_minor = #{categoryMinor},
                labor_shift = #{laborShift}, is_team_leader = #{isTeamLeader},
                org_unit_id = #{orgUnitId}, team_name = #{teamName}, is_active = #{isActive},
                upload_batch = #{uploadBatch}, is_distributed = #{isDistributed},
                distributed_at = #{distributedAt}, updated_at = CURRENT_TIMESTAMP
            WHERE id_card_no = #{idCardNo}
            """)
    int updateByIdCardNo(EmployeeBasic employee);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                identity_type, category_major, category_minor, labor_shift, is_team_leader,
                org_unit_id, team_name, is_active, upload_batch, is_distributed, distributed_at,
                created_at, updated_at
            FROM employee_basic WHERE id = #{id}
            """)
    EmployeeBasic findById(@Param("id") Long id);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                identity_type, category_major, category_minor, labor_shift, is_team_leader,
                org_unit_id, team_name, is_active, upload_batch, is_distributed, distributed_at,
                created_at, updated_at
            FROM employee_basic WHERE id_card_no = #{idCardNo}
            """)
    EmployeeBasic findByIdCardNo(@Param("idCardNo") String idCardNo);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                identity_type, category_major, category_minor, labor_shift, is_team_leader,
                org_unit_id, team_name, is_active, upload_batch, is_distributed, distributed_at,
                created_at, updated_at
            FROM employee_basic
            WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1
            ORDER BY id ASC
            """)
    List<EmployeeBasic> findDistributedByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                identity_type, category_major, category_minor, labor_shift, is_team_leader,
                org_unit_id, team_name, is_active, upload_batch, is_distributed, distributed_at,
                created_at, updated_at
            FROM employee_basic
            WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1 AND is_active = 0
            """)
    List<EmployeeBasic> findNonWorkingByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                identity_type, category_major, category_minor, labor_shift, is_team_leader,
                org_unit_id, team_name, is_active, upload_batch, is_distributed, distributed_at,
                created_at, updated_at
            FROM employee_basic
            WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1 AND is_active = 1
                AND age >= 59
            ORDER BY birth_date ASC
            """)
    List<EmployeeBasic> findNearRetirementByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Update("""
            UPDATE employee_basic
            SET is_distributed = 1, distributed_at = #{distributedAt}, updated_at = CURRENT_TIMESTAMP
            WHERE org_unit_id = #{orgUnitId} AND is_distributed = 0
            """)
    int distributeByOrgUnitId(@Param("orgUnitId") Long orgUnitId, @Param("distributedAt") LocalDateTime distributedAt);

    @Update("""
            UPDATE employee_basic
            SET work_type = #{workType}, team_name = #{teamName},
                labor_shift = #{laborShift}, is_team_leader = #{isTeamLeader},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateEditableFields(EmployeeBasic employee);

    @Select("""
            SELECT COUNT(1) FROM employee_basic WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1
            """)
    Long countDistributedByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                identity_type, category_major, category_minor, labor_shift, is_team_leader,
                org_unit_id, team_name, is_active, upload_batch, is_distributed, distributed_at,
                created_at, updated_at
            FROM employee_basic
            WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1
            ORDER BY id ASC
            LIMIT #{offset}, #{pageSize}
            """)
    List<EmployeeBasic> findDistributedByOrgUnitIdWithPage(@Param("orgUnitId") Long orgUnitId,
                                                           @Param("offset") int offset,
                                                           @Param("pageSize") int pageSize);

    @Select("""
            SELECT COUNT(1) FROM employee_basic WHERE is_distributed = 1
            """)
    Long countAllDistributed();

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                identity_type, category_major, category_minor, labor_shift, is_team_leader,
                org_unit_id, team_name, is_active, upload_batch, is_distributed, distributed_at,
                created_at, updated_at
            FROM employee_basic
            WHERE is_distributed = 1
            ORDER BY id ASC
            LIMIT #{offset}, #{pageSize}
            """)
    List<EmployeeBasic> findAllDistributedWithPage(@Param("offset") int offset,
                                                   @Param("pageSize") int pageSize);

    @Select("""
            SELECT COUNT(1) FROM employee_basic WHERE org_unit_id = #{orgUnitId}
            """)
    Long countByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                identity_type, category_major, category_minor, labor_shift, is_team_leader,
                org_unit_id, team_name, is_active, upload_batch, is_distributed, distributed_at,
                created_at, updated_at
            FROM employee_basic
            WHERE org_unit_id = #{orgUnitId}
            ORDER BY id ASC
            LIMIT #{offset}, #{pageSize}
            """)
    List<EmployeeBasic> findByOrgUnitIdWithPage(@Param("orgUnitId") Long orgUnitId,
                                                @Param("offset") int offset,
                                                @Param("pageSize") int pageSize);

    @Select("""
            SELECT COUNT(1) FROM employee_basic
            """)
    Long countAll();

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                identity_type, category_major, category_minor, labor_shift, is_team_leader,
                org_unit_id, team_name, is_active, upload_batch, is_distributed, distributed_at,
                created_at, updated_at
            FROM employee_basic
            ORDER BY id ASC
            LIMIT #{offset}, #{pageSize}
            """)
    List<EmployeeBasic> findAllWithPage(@Param("offset") int offset,
                                        @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                identity_type, category_major, category_minor, labor_shift, is_team_leader,
                org_unit_id, team_name, is_active, upload_batch, is_distributed, distributed_at,
                created_at, updated_at
            FROM employee_basic
            <if test="orgUnitId != null">
                WHERE org_unit_id = #{orgUnitId}
            </if>
            <if test="orgUnitId == null">
                WHERE 1=1
            </if>
            <if test="isDistributed != null">
                AND is_distributed = #{isDistributed}
            </if>
            ORDER BY id ASC
            </script>
            """)
    List<EmployeeBasic> findAll(@Param("orgUnitId") Long orgUnitId, @Param("isDistributed") Integer isDistributed);

    @Insert("""
            <script>
            INSERT INTO employee_basic (id_card_no, emp_name, gender, birth_date, age, work_type,
                identity_type, category_major, category_minor, labor_shift, is_team_leader,
                org_unit_id, team_name, is_active, upload_batch)
            VALUES
            <foreach collection="list" item="e" separator=",">
                (#{e.idCardNo}, #{e.empName}, #{e.gender}, #{e.birthDate}, #{e.age}, #{e.workType},
                #{e.identityType}, #{e.categoryMajor}, #{e.categoryMinor}, #{e.laborShift}, #{e.isTeamLeader},
                #{e.orgUnitId}, #{e.teamName}, #{e.isActive}, #{e.uploadBatch})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("list") List<EmployeeBasic> employees);
}
