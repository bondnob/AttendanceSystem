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
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, retirement_date)
            VALUES (#{idCardNo}, #{empName}, #{gender}, #{birthDate}, #{age}, #{workType},
                #{actualWorkType}, #{identityType}, #{categoryMajor}, #{categoryMinor}, #{laborShift},
                #{isTeamLeader}, #{orgUnitId}, #{teamName}, #{isActive}, #{uploadBatch}, #{retirementDate})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EmployeeBasic employee);

    @Update("""
            UPDATE employee_basic
            SET emp_name = #{empName}, gender = #{gender}, birth_date = #{birthDate}, age = #{age},
                work_type = #{workType}, actual_work_type = #{actualWorkType},
                identity_type = #{identityType},
                category_major = #{categoryMajor}, category_minor = #{categoryMinor},
                labor_shift = #{laborShift}, is_team_leader = #{isTeamLeader},
                org_unit_id = #{orgUnitId}, team_name = #{teamName}, is_active = #{isActive},
                upload_batch = #{uploadBatch}, is_distributed = #{isDistributed},
                distributed_at = #{distributedAt}, retirement_date = #{retirementDate},
                updated_at = CURRENT_TIMESTAMP
            WHERE id_card_no = #{idCardNo}
            """)
    int updateByIdCardNo(EmployeeBasic employee);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
            FROM employee_basic WHERE id = #{id}
            """)
    EmployeeBasic findById(@Param("id") Long id);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
            FROM employee_basic WHERE id_card_no = #{idCardNo}
            """)
    EmployeeBasic findByIdCardNo(@Param("idCardNo") String idCardNo);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
            FROM employee_basic
            WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1
            ORDER BY id ASC
            """)
    List<EmployeeBasic> findDistributedByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
            FROM employee_basic
            WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1 AND is_active = 0
            """)
    List<EmployeeBasic> findNonWorkingByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
            FROM employee_basic
            WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1 AND is_active = 1
                AND age >= (SELECT CAST(config_value AS UNSIGNED) FROM ledger_config WHERE config_key = 'retirement_age_threshold')
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
            SET work_type = #{workType}, actual_work_type = #{actualWorkType},
                team_name = #{teamName},
                labor_shift = #{laborShift}, is_team_leader = #{isTeamLeader},
                retirement_date = #{retirementDate},
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
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
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
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
            FROM employee_basic
            WHERE is_distributed = 1
            ORDER BY id ASC
            LIMIT #{offset}, #{pageSize}
            """)
    List<EmployeeBasic> findAllDistributedWithPage(@Param("offset") int offset,
                                                   @Param("pageSize") int pageSize);

    @Select("""
            SELECT COUNT(1) FROM employee_basic WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1 AND category_major = '在岗职工'
            """)
    Long countActiveDistributedByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Select("""
            SELECT COUNT(1) FROM employee_basic WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1 AND category_major = '非在岗职工'
            """)
    Long countNonWorkingDistributedByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Select("""
            SELECT COUNT(1) FROM employee_basic WHERE org_unit_id = #{orgUnitId}
            """)
    Long countByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Select("""
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
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
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
            FROM employee_basic
            ORDER BY id ASC
            LIMIT #{offset}, #{pageSize}
            """)
    List<EmployeeBasic> findAllWithPage(@Param("offset") int offset,
                                        @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
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

    @Select("""
            <script>
            SELECT COUNT(1) FROM employee_basic
            <choose>
                <when test="orgUnitId != null">WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1</when>
                <otherwise>WHERE is_distributed = 1</otherwise>
            </choose>
            <if test="categoryMajor != null and categoryMajor != ''"> AND category_major LIKE CONCAT('%', #{categoryMajor}, '%')</if>
            <if test="retirementAge != null"> AND age &gt;= #{retirementAge}</if>
            </script>
            """)
    Long countFiltered(@Param("orgUnitId") Long orgUnitId, @Param("categoryMajor") String categoryMajor, @Param("retirementAge") Integer retirementAge);

    @Select("""
            <script>
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
            FROM employee_basic
            <choose>
                <when test="orgUnitId != null">WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1</when>
                <otherwise>WHERE is_distributed = 1</otherwise>
            </choose>
            <if test="categoryMajor != null and categoryMajor != ''"> AND category_major LIKE CONCAT('%', #{categoryMajor}, '%')</if>
            <if test="retirementAge != null"> AND age &gt;= #{retirementAge}</if>
            ORDER BY id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<EmployeeBasic> findFilteredWithPage(@Param("orgUnitId") Long orgUnitId, @Param("categoryMajor") String categoryMajor, @Param("retirementAge") Integer retirementAge,
                                             @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
            FROM employee_basic
            <choose>
                <when test="orgUnitId != null">WHERE org_unit_id = #{orgUnitId} AND is_distributed = 1</when>
                <otherwise>WHERE is_distributed = 1</otherwise>
            </choose>
            <if test="categoryMajor != null and categoryMajor != ''"> AND category_major LIKE CONCAT('%', #{categoryMajor}, '%')</if>
            <if test="retirementAge != null"> AND age &gt;= #{retirementAge}</if>
            ORDER BY id ASC
            </script>
            """)
    List<EmployeeBasic> findFiltered(@Param("orgUnitId") Long orgUnitId, @Param("categoryMajor") String categoryMajor, @Param("retirementAge") Integer retirementAge);

    @Insert("""
            <script>
            INSERT INTO employee_basic (id_card_no, emp_name, gender, birth_date, age, work_type,
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, retirement_date)
            VALUES
            <foreach collection="list" item="e" separator=",">
                (#{e.idCardNo}, #{e.empName}, #{e.gender}, #{e.birthDate}, #{e.age}, #{e.workType},
                #{e.actualWorkType}, #{e.identityType}, #{e.categoryMajor}, #{e.categoryMinor},
                #{e.laborShift}, #{e.isTeamLeader}, #{e.orgUnitId}, #{e.teamName}, #{e.isActive},
                #{e.uploadBatch}, #{e.retirementDate})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("list") List<EmployeeBasic> employees);

    @Select("""
            <script>
            SELECT COUNT(1) FROM employee_basic
            WHERE org_unit_id IN
            <foreach collection="orgUnitIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            AND is_distributed = 1
            </script>
            """)
    Long countByOrgUnitIds(@Param("orgUnitIds") List<Long> orgUnitIds);

    @Select("""
            <script>
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
            FROM employee_basic
            WHERE org_unit_id IN
            <foreach collection="orgUnitIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            AND is_distributed = 1
            ORDER BY org_unit_id, id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<EmployeeBasic> findByOrgUnitIdsWithPage(@Param("orgUnitIds") List<Long> orgUnitIds,
                                                  @Param("offset") int offset,
                                                  @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(1) FROM employee_basic
            WHERE org_unit_id IN
            <foreach collection="orgUnitIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            AND is_distributed = 1
            <if test="categoryMajor != null and categoryMajor != ''"> AND category_major LIKE CONCAT('%', #{categoryMajor}, '%')</if>
            <if test="retirementAge != null"> AND age &gt;= #{retirementAge}</if>
            </script>
            """)
    Long countFilteredByOrgUnitIds(@Param("orgUnitIds") List<Long> orgUnitIds,
                                    @Param("categoryMajor") String categoryMajor,
                                    @Param("retirementAge") Integer retirementAge);

    @Select("""
            <script>
            SELECT id, id_card_no, emp_name, gender, birth_date, age, work_type,
                actual_work_type, identity_type, category_major, category_minor, labor_shift,
                is_team_leader, org_unit_id, team_name, is_active, upload_batch, is_distributed,
                distributed_at, retirement_date, created_at, updated_at
            FROM employee_basic
            WHERE org_unit_id IN
            <foreach collection="orgUnitIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            AND is_distributed = 1
            <if test="categoryMajor != null and categoryMajor != ''"> AND category_major LIKE CONCAT('%', #{categoryMajor}, '%')</if>
            <if test="retirementAge != null"> AND age &gt;= #{retirementAge}</if>
            ORDER BY org_unit_id, id ASC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<EmployeeBasic> findFilteredByOrgUnitIdsWithPage(@Param("orgUnitIds") List<Long> orgUnitIds,
                                                          @Param("categoryMajor") String categoryMajor,
                                                          @Param("retirementAge") Integer retirementAge,
                                                          @Param("offset") int offset,
                                                          @Param("pageSize") int pageSize);
}
