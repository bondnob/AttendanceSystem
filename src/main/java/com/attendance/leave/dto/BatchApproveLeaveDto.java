package com.attendance.leave.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class BatchApproveLeaveDto {

    @NotEmpty(message = "请假单不能为空")
    private List<Long> leaveIds;

    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    private String comment;

    private MultipartFile signatureFile;

    private String signatureUrl;
}
