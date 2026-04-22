package com.attendance.leave.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class SignLeaveRequestDto {

    private String comment;

    @NotNull(message = "签名文件不能为空")
    private MultipartFile signatureFile;
}
