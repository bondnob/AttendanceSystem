package com.attendance.leave.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadApprovalSignatureDto {

    private MultipartFile signatureFile;
}
