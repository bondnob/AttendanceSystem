package com.attendance.ledger.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerMonthCompareResponse {

    private String currentMonth;
    private String previousMonth;
    private List<CompareItem> differences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompareItem {
        private String empName;
        private String idCardNo;
        private String field;
        private String previousValue;
        private String currentValue;
        private String changeType;
    }
}
