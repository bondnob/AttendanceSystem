package com.attendance.common;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PageResponse<T> {

    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private List<T> records;
}
