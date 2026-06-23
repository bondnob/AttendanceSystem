package com.attendance.leave.service;

import com.attendance.common.PageResponse;
import com.attendance.leave.dto.BatchLeavePdfRequest;
import com.attendance.leave.dto.BatchLeavePdfResponse;
import com.attendance.leave.dto.LeaveDetailResponse;
import com.attendance.leave.dto.LeaveListItemResponse;
import com.attendance.leave.dto.LeavePdfResponse;
import com.attendance.leave.dto.LeaveStatusOptionResponse;
import com.attendance.leave.dto.PendingSummaryResponse;
import com.attendance.leave.dto.SelectedApproverResponse;
import com.attendance.leave.model.LeaveType;
import java.util.List;

public interface LeaveQueryService {

    LeaveDetailResponse getLeaveDetail(Long leaveId);

    LeavePdfResponse getLeavePdf(Long leaveId);

    BatchLeavePdfResponse batchDownloadPdf(BatchLeavePdfRequest dto);

    List<SelectedApproverResponse> getSelectedApprovers(Long leaveId);

    PageResponse<LeaveListItemResponse> listLeaves(String status, Long leaveTypeId, Integer pageNum, Integer pageSize);

    PageResponse<LeaveListItemResponse> listRecentThreeMonthApprovalLeaves(String status, Long leaveTypeId, Integer pageNum, Integer pageSize);

    PageResponse<LeaveListItemResponse> listAllLeaves(String status, Long leaveTypeId, String applicantName, Integer pageNum, Integer pageSize);

    List<LeaveType> listLeaveTypes();

    List<LeaveStatusOptionResponse> listLeaveStatuses();

    PendingSummaryResponse getPendingSummary();
}
