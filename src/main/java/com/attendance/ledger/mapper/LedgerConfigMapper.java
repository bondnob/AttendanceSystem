package com.attendance.ledger.mapper;

import com.attendance.ledger.model.LedgerConfig;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LedgerConfigMapper {

    @Select("SELECT id, config_key, config_value, description, updated_at FROM ledger_config")
    List<LedgerConfig> findAll();

    @Select("SELECT id, config_key, config_value, description, updated_at FROM ledger_config WHERE config_key = #{configKey}")
    LedgerConfig findByKey(@Param("configKey") String configKey);

    @Update("UPDATE ledger_config SET config_value = #{configValue}, updated_at = CURRENT_TIMESTAMP WHERE config_key = #{configKey}")
    int updateValue(@Param("configKey") String configKey, @Param("configValue") String configValue);
}
