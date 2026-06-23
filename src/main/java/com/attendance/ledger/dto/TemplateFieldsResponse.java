package com.attendance.ledger.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateFieldsResponse {

    /** 模板中的标题（如"运转一车间 现员分布台账"） */
    private String title;

    /** 该车间模板需要的所有字段 */
    private List<FieldItem> fields;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldItem {
        /** 数据库字段名，如 jiaBan1、extra:一调半、shiftCategory */
        private String name;
        /** 模板中显示的中文名，如 甲班、行配班组、班制 */
        private String label;
        /** 是否为班次列（true=班次，false=固定列如班制/日勤/职务） */
        private boolean shift;
    }
}
