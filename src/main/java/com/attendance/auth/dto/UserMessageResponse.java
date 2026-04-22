package com.attendance.auth.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserMessageResponse {

    private Long id;
    private String title;
    private String content;
    private Long senderUserId;
    private String senderName;
    private LocalDateTime createdAt;
}
