package com.attendance.admin.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamNameResponse {

    private Long id;
    private Long orgUnitId;
    private String orgUnitName;
    private String teamName;
    private String shiftCategory;
    private Integer sortNo;
    private Integer isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
