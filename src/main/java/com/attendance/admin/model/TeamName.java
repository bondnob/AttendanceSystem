package com.attendance.admin.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TeamName {

    private Long id;
    private Long orgUnitId;
    private String teamName;
    private String shiftCategory;
    private Integer sortNo;
    private Integer isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
