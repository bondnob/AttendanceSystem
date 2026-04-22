package com.attendance.leave.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CreateLeaveRequestDto {

    @NotNull(message = "申请人不能为空")
    private Long applicantId;

    @NotBlank(message = "被请假员工姓名不能为空")
    private String applicantName;

    @NotBlank(message = "职名不能为空")
    private String jobTitle;

    @NotBlank(message = "班组长信息不能为空")
    private String teamLeaderInfo;

    @NotBlank(message = "人员类别不能为空")
    private String applicantType;

    @NotNull(message = "假别不能为空")
    private Long leaveTypeId;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "请假天数不能为空")
    @DecimalMin(value = "0.01", message = "请假天数必须大于0")
    private BigDecimal leaveDays;

    private String reason;

    private String remark;
}
