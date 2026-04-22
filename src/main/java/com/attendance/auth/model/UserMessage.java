package com.attendance.auth.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class UserMessage {

    private Long id;
    private Long senderUserId;
    private Long targetUserId;
    private String title;
    private String content;
    private LocalDateTime createdAt;
}
