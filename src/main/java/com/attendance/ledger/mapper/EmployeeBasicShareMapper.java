package com.attendance.ledger.mapper;

import com.attendance.ledger.model.EmployeeBasicShare;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmployeeBasicShareMapper {

    @Insert("""
            INSERT INTO employee_basic_share (org_unit_id, shared_user_id)
            VALUES (#{orgUnitId}, #{sharedUserId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EmployeeBasicShare share);

    @Delete("""
            DELETE FROM employee_basic_share
            WHERE org_unit_id = #{orgUnitId} AND shared_user_id = #{sharedUserId}
            """)
    int deleteByOrgUnitAndUser(@Param("orgUnitId") Long orgUnitId,
                               @Param("sharedUserId") Long sharedUserId);

    @Select("""
            SELECT DISTINCT org_unit_id FROM employee_basic_share
            WHERE shared_user_id = #{sharedUserId}
            """)
    List<Long> findSharedOrgUnitIdsByUserId(@Param("sharedUserId") Long sharedUserId);

    @Select("""
            SELECT id, org_unit_id, shared_user_id, created_at
            FROM employee_basic_share
            WHERE org_unit_id = #{orgUnitId}
            """)
    List<EmployeeBasicShare> findByOrgUnitId(@Param("orgUnitId") Long orgUnitId);

    @Select("""
            SELECT COUNT(1) FROM employee_basic_share
            WHERE org_unit_id = #{orgUnitId} AND shared_user_id = #{sharedUserId}
            """)
    int countByOrgUnitAndUser(@Param("orgUnitId") Long orgUnitId,
                              @Param("sharedUserId") Long sharedUserId);
}
