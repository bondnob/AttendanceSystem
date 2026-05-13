package com.attendance.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class HandwrittenSignatureDto {

    @NotBlank(message = "签名类型不能为空")
    private String applicantType;

    @NotNull(message = "签名文件不能为空")
    private MultipartFile signatureFile;
}
