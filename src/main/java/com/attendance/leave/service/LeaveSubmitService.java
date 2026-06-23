package com.attendance.leave.service;

import com.attendance.leave.dto.CreateLeaveRequestDto;
import com.attendance.leave.dto.CancelLeaveRequestDto;
import com.attendance.leave.dto.LeaveDetailResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface LeaveSubmitService {

    LeaveDetailResponse createLeave(CreateLeaveRequestDto dto);

    LeaveDetailResponse updateRejectedLeave(Long leaveId, CreateLeaveRequestDto dto);

    void deleteRejectedLeave(Long leaveId);

    LeaveDetailResponse cancelLeave(Long leaveId, CancelLeaveRequestDto dto);

    LeaveDetailResponse updateSubmittedAt(Long leaveId, LocalDateTime submittedAt);

    LeaveDetailResponse updateSignatureDate(Long leaveId, Integer stepNo, LocalDate signatureDate);
}
