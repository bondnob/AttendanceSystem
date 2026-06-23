-- 安全迁移：删除 extra_shift 编号列（改用 extra_shift_json 存储动态班组数据）
DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS drop_extra_shift_columns()
BEGIN
    DECLARE col_count INT;

    SELECT COUNT(*) INTO col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'staff_ledger_detail'
      AND COLUMN_NAME = 'extra_shift1a';

    IF col_count > 0 THEN
        ALTER TABLE staff_ledger_detail
            DROP COLUMN IF EXISTS extra_shift1a,
            DROP COLUMN IF EXISTS extra_shift1b,
            DROP COLUMN IF EXISTS extra_shift2a,
            DROP COLUMN IF EXISTS extra_shift2b,
            DROP COLUMN IF EXISTS extra_shift3a,
            DROP COLUMN IF EXISTS extra_shift3b,
            DROP COLUMN IF EXISTS extra_shift4a,
            DROP COLUMN IF EXISTS extra_shift4b,
            DROP COLUMN IF EXISTS extra_shift5a,
            DROP COLUMN IF EXISTS extra_shift5b;
        SELECT 'extra_shift 编号列已删除' AS result;
    ELSE
        SELECT '列已不存在，跳过' AS result;
    END IF;
END$$

DELIMITER ;

CALL drop_extra_shift_columns();
DROP PROCEDURE drop_extra_shift_columns;
