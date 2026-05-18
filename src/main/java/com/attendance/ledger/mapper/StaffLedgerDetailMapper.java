package com.attendance.ledger.mapper;

import com.attendance.ledger.model.StaffLedgerDetail;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StaffLedgerDetailMapper {

    @Insert("""
            INSERT INTO staff_ledger_detail (ledger_id, employee_basic_id, station_point, sort_no)
            VALUES (#{ledgerId}, #{employeeBasicId}, #{stationPoint}, #{sortNo})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StaffLedgerDetail detail);

    @Select("""
            SELECT id, ledger_id, employee_basic_id, station_point, sort_no, created_at, updated_at
            FROM staff_ledger_detail WHERE id = #{id}
            """)
    StaffLedgerDetail findById(@Param("id") Long id);

    @Select("""
            SELECT id, ledger_id, employee_basic_id, station_point, sort_no, created_at, updated_at
            FROM staff_ledger_detail WHERE ledger_id = #{ledgerId} ORDER BY sort_no ASC, id ASC
            """)
    List<StaffLedgerDetail> findByLedgerId(@Param("ledgerId") Long ledgerId);

    @Update("""
            UPDATE staff_ledger_detail
            SET station_point = #{stationPoint}, sort_no = #{sortNo}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(StaffLedgerDetail detail);

    @Delete("DELETE FROM staff_ledger_detail WHERE ledger_id = #{ledgerId}")
    int deleteByLedgerId(@Param("ledgerId") Long ledgerId);

    @Insert("""
            <script>
            INSERT INTO staff_ledger_detail (ledger_id, employee_basic_id, station_point, sort_no)
            VALUES
            <foreach collection="list" item="d" separator=",">
                (#{d.ledgerId}, #{d.employeeBasicId}, #{d.stationPoint}, #{d.sortNo})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("list") List<StaffLedgerDetail> details);

    @Select("SELECT COUNT(1) FROM staff_ledger_detail WHERE ledger_id = #{ledgerId}")
    Long countByLedgerId(@Param("ledgerId") Long ledgerId);
}
