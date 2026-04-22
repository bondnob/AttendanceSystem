package com.attendance.leave.mapper;

import com.attendance.leave.model.LeaveType;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LeaveTypeMapper {

    @Select("""
            SELECT id, leave_code, leave_name, default_days, day_unit, calc_rule, description
            FROM leave_type
            WHERE id = #{id}
            """)
    LeaveType findById(@Param("id") Long id);

    @Select("""
            SELECT id, leave_code, leave_name, default_days, day_unit, calc_rule, description
            FROM leave_type
            ORDER BY id ASC
            """)
    List<LeaveType> findAll();
}
