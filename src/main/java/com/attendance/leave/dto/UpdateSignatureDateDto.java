package com.attendance.leave.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class UpdateSignatureDateDto {

    @NotNull(message = "审批节点不能为空")
    private Integer stepNo;

    @NotNull(message = "签字日期不能为空")
    private LocalDate signatureDate;
}
