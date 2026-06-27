package com.attendance.ledger.mapper;

import com.attendance.ledger.model.StaffLedger;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StaffLedgerMapper {

    @Insert("""
            INSERT INTO staff_ledger (org_unit_id, ledger_month, status, created_by)
            VALUES (#{orgUnitId}, #{ledgerMonth}, #{status}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StaffLedger ledger);

    @Select("""
            SELECT id, org_unit_id, ledger_month, status, in_work_count, remark, change_description,
                director_user_id, director_opinion, director_approved_at,
                hr_user_id, hr_opinion, hr_approved_at, shared_user_ids,
                submitted_at, created_by, created_at, updated_at
            FROM staff_ledger WHERE id = #{id}
            """)
    StaffLedger findById(@Param("id") Long id);

    @Select("""
            SELECT id, org_unit_id, ledger_month, status, in_work_count, remark, change_description,
                director_user_id, director_opinion, director_approved_at,
                hr_user_id, hr_opinion, hr_approved_at, shared_user_ids,
                submitted_at, created_by, created_at, updated_at
            FROM staff_ledger
            WHERE org_unit_id = #{orgUnitId} AND ledger_month = #{ledgerMonth}
            """)
    StaffLedger findByOrgUnitAndMonth(@Param("orgUnitId") Long orgUnitId, @Param("ledgerMonth") String ledgerMonth);

    @Select("""
            <script>
            SELECT id, org_unit_id, ledger_month, status, in_work_count, remark, change_description,
                director_user_id, director_opinion, director_approved_at,
                hr_user_id, hr_opinion, hr_approved_at, shared_user_ids,
                submitted_at, created_by, created_at, updated_at
            FROM staff_ledger
            <if test="status != null">
                WHERE status = #{status}
            </if>
            <if test="status == null">
                WHERE 1=1
            </if>
            <if test="ledgerMonth != null">
                AND ledger_month = #{ledgerMonth}
            </if>
            ORDER BY updated_at DESC
            </script>
            """)
    List<StaffLedger> findByCondition(@Param("status") String status, @Param("ledgerMonth") String ledgerMonth);

    @Select("""
            SELECT id, org_unit_id, ledger_month, status, in_work_count, remark, change_description,
                director_user_id, director_opinion, director_approved_at,
                hr_user_id, hr_opinion, hr_approved_at, shared_user_ids,
                submitted_at, created_by, created_at, updated_at
            FROM staff_ledger WHERE status = #{status} ORDER BY updated_at DESC
            """)
    List<StaffLedger> findByStatus(@Param("status") String status);

    @Select({"<script>",
            "SELECT COUNT(1) FROM staff_ledger",
            "<where>",
            "  <if test='orgUnitId != null'> org_unit_id = #{orgUnitId}</if>",
            "  <if test='status != null and status != \"\"'> AND status = #{status}</if>",
            "</where>",
            "</script>"})
    Long countByCondition(@Param("orgUnitId") Long orgUnitId, @Param("status") String status);

    @Select({"<script>",
            "SELECT id, org_unit_id, ledger_month, status, in_work_count, remark, change_description,",
            "    director_user_id, director_opinion, director_approved_at,",
            "    hr_user_id, hr_opinion, hr_approved_at, shared_user_ids,",
            "    submitted_at, created_by, created_at, updated_at",
            "FROM staff_ledger",
            "<where>",
            "  <if test='orgUnitId != null'> org_unit_id = #{orgUnitId}</if>",
            "  <if test='status != null and status != \"\"'> AND status = #{status}</if>",
            "</where>",
            "ORDER BY updated_at DESC",
            "LIMIT #{offset}, #{pageSize}",
            "</script>"})
    List<StaffLedger> findPageByCondition(@Param("orgUnitId") Long orgUnitId,
                                           @Param("status") String status,
                                           @Param("offset") Integer offset,
                                           @Param("pageSize") Integer pageSize);

    @Update("""
            UPDATE staff_ledger
            SET status = #{status}, in_work_count = #{inWorkCount}, remark = #{remark},
                change_description = #{changeDescription}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateStatusAndCounts(StaffLedger ledger);

    @Update("""
            UPDATE staff_ledger
            SET status = #{status}, director_user_id = #{directorUserId},
                director_opinion = #{directorOpinion}, director_approved_at = #{directorApprovedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateDirectorApproval(StaffLedger ledger);

    @Update("""
            UPDATE staff_ledger
            SET status = #{status}, hr_user_id = #{hrUserId},
                hr_opinion = #{hrOpinion}, hr_approved_at = #{hrApprovedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateHrReview(StaffLedger ledger);

    @Update("""
            UPDATE staff_ledger
            SET submitted_at = #{submittedAt}, status = #{status}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateSubmitted(StaffLedger ledger);

    @Update("""
            UPDATE staff_ledger
            SET status = 'DRAFT', director_user_id = NULL, director_opinion = NULL, director_approved_at = NULL,
                hr_user_id = NULL, hr_opinion = NULL, hr_approved_at = NULL, submitted_at = NULL,
                in_work_count = NULL, remark = NULL, change_description = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int resetForSync(StaffLedger ledger);

    @Update("""
            UPDATE staff_ledger
            SET shared_user_ids = #{sharedUserIds}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateSharedUserIds(StaffLedger ledger);

    @Select("""
            SELECT id, org_unit_id, ledger_month, status, in_work_count, remark, change_description,
                director_user_id, director_opinion, director_approved_at,
                hr_user_id, hr_opinion, hr_approved_at, shared_user_ids,
                submitted_at, created_by, created_at, updated_at
            FROM staff_ledger
            WHERE FIND_IN_SET(#{userId}, shared_user_ids) > 0
            ORDER BY updated_at DESC
            """)
    List<StaffLedger> findBySharedUserId(@Param("userId") Long userId);
}
