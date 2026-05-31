package com.attendance.leave.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TeamLeaderSignatureDateDto {

    @NotNull(message = "签字日期不能为空")
    private LocalDate signatureDate;
}
