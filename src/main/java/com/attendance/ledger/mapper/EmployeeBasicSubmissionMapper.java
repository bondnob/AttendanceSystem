package com.attendance.ledger.mapper;

import com.attendance.ledger.model.EmployeeBasicSubmission;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EmployeeBasicSubmissionMapper {

    @Insert("""
            INSERT INTO employee_basic_submission (org_unit_id, status, submitted_by, submitted_at)
            VALUES (#{orgUnitId}, #{status}, #{submittedBy}, #{submittedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EmployeeBasicSubmission submission);

    @Select("""
            SELECT id, org_unit_id, status, submitted_by, submitted_at, created_at, updated_at
            FROM employee_basic_submission
            WHERE org_unit_id = #{orgUnitId}
            ORDER BY submitted_at DESC
            LIMIT 1
            """)
    EmployeeBasicSubmission findLatestByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Update("""
            UPDATE employee_basic_submission
            SET status = #{status}, submitted_by = #{submittedBy}, submitted_at = #{submittedAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(EmployeeBasicSubmission submission);
}
