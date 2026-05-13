package com.attendance.admin.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateUserSignatureRequest {

    private MultipartFile signatureFile;
    private String signatureUrl;
}
