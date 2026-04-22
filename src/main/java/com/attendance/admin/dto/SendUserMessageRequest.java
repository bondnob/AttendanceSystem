package com.attendance.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class SendUserMessageRequest {

    @NotEmpty(message = "请选择接收账号")
    private List<Long> targetUserIds;

    @NotBlank(message = "提示标题不能为空")
    private String title;

    @NotBlank(message = "提示内容不能为空")
    private String content;
}
