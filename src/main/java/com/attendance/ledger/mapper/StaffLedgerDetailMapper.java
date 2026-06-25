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
            INSERT INTO staff_ledger_detail (ledger_id, employee_basic_id, station_point, team_name, shift_category, work_type, sort_no,
                jia_ban1, jia_ban2, yi_ban1, yi_ban2, bing_ban1, bing_ban2, ding_ban1, ding_ban2, yu_bei1, yu_bei2, yu_bei3, yu_bei4, daily_name, identity_type,
                extra_shift_json)
            VALUES (#{ledgerId}, #{employeeBasicId}, #{stationPoint}, #{teamName}, #{shiftCategory}, #{workType}, #{sortNo},
                #{jiaBan1}, #{jiaBan2}, #{yiBan1}, #{yiBan2}, #{bingBan1}, #{bingBan2}, #{dingBan1}, #{dingBan2}, #{yuBei1}, #{yuBei2}, #{yuBei3}, #{yuBei4}, #{dailyName}, #{identityType},
                #{extraShiftJson})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StaffLedgerDetail detail);

    @Select("""
            SELECT id, ledger_id, employee_basic_id, station_point, team_name, shift_category, work_type, sort_no,
                jia_ban1, jia_ban2, yi_ban1, yi_ban2, bing_ban1, bing_ban2, ding_ban1, ding_ban2, yu_bei1, yu_bei2, yu_bei3, yu_bei4, daily_name, identity_type,
                extra_shift_json,
                created_at, updated_at
            FROM staff_ledger_detail WHERE id = #{id}
            """)
    StaffLedgerDetail findById(@Param("id") Long id);

    @Select("""
            SELECT id, ledger_id, employee_basic_id, station_point, team_name, shift_category, work_type, sort_no,
                jia_ban1, jia_ban2, yi_ban1, yi_ban2, bing_ban1, bing_ban2, ding_ban1, ding_ban2, yu_bei1, yu_bei2, yu_bei3, yu_bei4, daily_name, identity_type,
                extra_shift_json,
                created_at, updated_at
            FROM staff_ledger_detail WHERE ledger_id = #{ledgerId} ORDER BY sort_no ASC, id ASC
            """)
    List<StaffLedgerDetail> findByLedgerId(@Param("ledgerId") Long ledgerId);

    @Select("""
            SELECT id, ledger_id, employee_basic_id, station_point, team_name, shift_category, work_type, sort_no,
                jia_ban1, jia_ban2, yi_ban1, yi_ban2, bing_ban1, bing_ban2, ding_ban1, ding_ban2, yu_bei1, yu_bei2, yu_bei3, yu_bei4, daily_name, identity_type,
                extra_shift_json,
                created_at, updated_at
            FROM staff_ledger_detail WHERE ledger_id = #{ledgerId} AND employee_basic_id = #{employeeBasicId}
            """)
    StaffLedgerDetail findByLedgerIdAndEmployeeBasicId(@Param("ledgerId") Long ledgerId, @Param("employeeBasicId") Long employeeBasicId);

    @Update("""
            UPDATE staff_ledger_detail
            SET station_point = #{stationPoint}, team_name = #{teamName}, shift_category = #{shiftCategory},
                work_type = #{workType}, sort_no = #{sortNo},
                jia_ban1 = #{jiaBan1}, jia_ban2 = #{jiaBan2},
                yi_ban1 = #{yiBan1}, yi_ban2 = #{yiBan2},
                bing_ban1 = #{bingBan1}, bing_ban2 = #{bingBan2},
                ding_ban1 = #{dingBan1}, ding_ban2 = #{dingBan2},
                yu_bei1 = #{yuBei1}, yu_bei2 = #{yuBei2}, yu_bei3 = #{yuBei3}, yu_bei4 = #{yuBei4},
                daily_name = #{dailyName}, identity_type = #{identityType},
                extra_shift_json = #{extraShiftJson},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(StaffLedgerDetail detail);

    @Delete("DELETE FROM staff_ledger_detail WHERE ledger_id = #{ledgerId}")
    int deleteByLedgerId(@Param("ledgerId") Long ledgerId);

    @Insert("""
            <script>
            INSERT INTO staff_ledger_detail (ledger_id, employee_basic_id, station_point, team_name, shift_category, work_type, sort_no,
                jia_ban1, jia_ban2, yi_ban1, yi_ban2, bing_ban1, bing_ban2, ding_ban1, ding_ban2, yu_bei1, yu_bei2, yu_bei3, yu_bei4, daily_name, identity_type,
                extra_shift_json)
            VALUES
            <foreach collection="list" item="d" separator=",">
                (#{d.ledgerId}, #{d.employeeBasicId}, #{d.stationPoint}, #{d.teamName}, #{d.shiftCategory}, #{d.workType}, #{d.sortNo},
                 #{d.jiaBan1}, #{d.jiaBan2}, #{d.yiBan1}, #{d.yiBan2}, #{d.bingBan1}, #{d.bingBan2}, #{d.dingBan1}, #{d.dingBan2}, #{d.yuBei1}, #{d.yuBei2}, #{d.yuBei3}, #{d.yuBei4}, #{d.dailyName}, #{d.identityType},
                 #{d.extraShiftJson})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("list") List<StaffLedgerDetail> details);

    @Select("SELECT COUNT(1) FROM staff_ledger_detail WHERE ledger_id = #{ledgerId}")
    Long countByLedgerId(@Param("ledgerId") Long ledgerId);
}
