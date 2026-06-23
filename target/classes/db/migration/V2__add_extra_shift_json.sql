-- 新增 extra_shift_json 列，用于存储动态额外班次数据
-- JSON格式: {"班次名":["姓名1","姓名2"], ...}
ALTER TABLE staff_ledger_detail ADD COLUMN extra_shift_json TEXT NULL DEFAULT NULL COMMENT '额外班次JSON数据';
