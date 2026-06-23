package com.attendance.leave.service;

import com.attendance.leave.dto.ApproveLeaveWithSignatureDto;
import com.attendance.leave.dto.ApprovalSignatureUploadResponse;
import com.attendance.leave.dto.BatchApproveLeaveDto;
import com.attendance.leave.dto.BatchApproveLeaveResponse;
import com.attendance.leave.dto.HandwrittenSignatureDto;
import com.attendance.leave.dto.LeaveDetailResponse;
import com.attendance.leave.dto.SelectApproversDto;
import java.time.LocalDate;

public interface LeaveApprovalService {

    LeaveDetailResponse approve(Long leaveId, ApproveLeaveWithSignatureDto dto);

    BatchApproveLeaveResponse batchApprove(BatchApproveLeaveDto dto);

    ApprovalSignatureUploadResponse uploadApprovalSignature(Long leaveId, com.attendance.leave.dto.UploadApprovalSignatureDto dto);

    LeaveDetailResponse uploadHandwrittenSignature(Long leaveId, HandwrittenSignatureDto dto);

    LeaveDetailResponse uploadTeamLeaderSignatureDate(Long leaveId, LocalDate signatureDate);

    LeaveDetailResponse selectApprovers(Long leaveId, SelectApproversDto dto);

    LeaveDetailResponse reSelectApprovers(Long leaveId, SelectApproversDto dto);
}
