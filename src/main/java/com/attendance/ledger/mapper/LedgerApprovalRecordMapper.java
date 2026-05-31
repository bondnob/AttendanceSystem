package com.attendance.ledger.mapper;

import com.attendance.ledger.model.LedgerApprovalRecord;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LedgerApprovalRecordMapper {

    @Insert("""
            INSERT INTO ledger_approval_record (ledger_id, step, action, opinion, operator_user_id)
            VALUES (#{ledgerId}, #{step}, #{action}, #{opinion}, #{operatorUserId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LedgerApprovalRecord record);

    @Select("""
            SELECT id, ledger_id, step, action, opinion, operator_user_id, created_at
            FROM ledger_approval_record WHERE ledger_id = #{ledgerId} ORDER BY created_at ASC
            """)
    List<LedgerApprovalRecord> findByLedgerId(@Param("ledgerId") Long ledgerId);

    @Delete("DELETE FROM ledger_approval_record WHERE ledger_id = #{ledgerId}")
    int deleteByLedgerId(@Param("ledgerId") Long ledgerId);
}
