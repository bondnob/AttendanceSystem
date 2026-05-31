package com.attendance.leave.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ApproveLeaveWithSignatureDto {

    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    private String comment;

    private MultipartFile signatureFile;

    private String signatureUrl;

    private LocalDate signatureDate;

    private LocalDateTime approvedAt;
}
