-- 删除 staff_ledger_detail 表中的 extra_shift 编号列（改用 extra_shift_json 存储动态班组数据）
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
