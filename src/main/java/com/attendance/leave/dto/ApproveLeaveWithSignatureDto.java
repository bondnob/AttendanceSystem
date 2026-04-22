package com.attendance.leave.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ApproveLeaveWithSignatureDto {

    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    private String comment;

    private MultipartFile signatureFile;

    private String signatureUrl;
}
