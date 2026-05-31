package com.attendance.ledger.controller;

import com.attendance.common.ApiResponse;
import com.attendance.common.PageResponse;
import com.attendance.ledger.dto.ApproveLedgerRequest;
import com.attendance.ledger.dto.AttendanceAdminResponse;
import com.attendance.ledger.dto.DistributeLedgerRequest;
import com.attendance.ledger.dto.EmployeeBasicResponse;
import com.attendance.ledger.dto.LedgerConfigRequest;
import com.attendance.ledger.dto.LedgerMonthCompareResponse;
import com.attendance.ledger.dto.LedgerPendingResponse;
import com.attendance.ledger.dto.LedgerResponse;
import com.attendance.ledger.dto.SaveLedgerRequest;
import com.attendance.ledger.dto.UpdateEmployeeBasicRequest;
import com.attendance.ledger.service.LedgerExportService;
import com.attendance.ledger.service.StaffLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin
@Tag(name = "现员台账接口")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ledger")
public class StaffLedgerController {

    private final StaffLedgerService staffLedgerService;
    private final LedgerExportService ledgerExportService;

    // ==================== 现员基础表相关 ====================

    @Operation(summary = "导入现员基础表(Excel)", description = "超级管理员上传现员基础表Excel文件。")
    @PostMapping("/import-basic")
    public ApiResponse<Map<String, Object>> importEmployeeBasic(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success("导入成功", staffLedgerService.uploadEmployeeBasic(file));
    }

    @Operation(summary = "下发现员基础表", description = "超级管理员将现员基础表下发给指定部门（支持批量）。")
    @PostMapping("/distribute")
    public ApiResponse<Void> distributeToOrg(@Valid @RequestBody DistributeLedgerRequest request) {
        staffLedgerService.distributeToUsers(request.getUserIds());
        return ApiResponse.success("下发成功", null);
    }

    @Operation(summary = "获取本部门现员基础表", description = "考勤管理员获取本部门已下发的现员基础表，超级管理员返回全部。传orgUnitId则按指定部门查询。")
    @GetMapping("/basic/my")
    public ApiResponse<PageResponse<EmployeeBasicResponse>> getMyEmployeeBasic(
            @RequestParam(required = false) Long orgUnitId,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(staffLedgerService.getMyEmployeeBasic(orgUnitId, pageNum, pageSize));
    }

    @Operation(summary = "修改现员基础表", description = "考勤管理员修改现员基础表中的工种、部门班组、劳动班制、班组长。")
    @PutMapping("/basic/update")
    public ApiResponse<EmployeeBasicResponse> updateEmployeeBasic(@Valid @RequestBody UpdateEmployeeBasicRequest request) {
        return ApiResponse.success("修改成功", staffLedgerService.updateEmployeeBasic(request));
    }

    @Operation(summary = "导出现员基础表(Excel)")
    @GetMapping("/basic/export")
    public ResponseEntity<byte[]> exportEmployeeBasic(@RequestParam(required = false) Long orgUnitId) {
        try {
            byte[] data = ledgerExportService.exportEmployeeBasicToExcel(orgUnitId);
            String filename = URLEncoder.encode("现员基础表.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    // ==================== 现员台账相关 ====================

    @Operation(summary = "同步到现员台账", description = "将现员基础表数据同步到现员分布台账。")
    @PostMapping("/sync")
    public ApiResponse<LedgerResponse> syncToLedger(@RequestParam(required = false) String month) {
        return ApiResponse.success("同步成功", staffLedgerService.syncToLedger(month));
    }

    @Operation(summary = "获取本部门台账", description = "考勤管理员获取本部门的现员分布台账。")
    @GetMapping("/my")
    public ApiResponse<LedgerResponse> getMyLedger(@RequestParam(required = false) String month) {
        return ApiResponse.success(staffLedgerService.getMyLedger(month));
    }

    @Operation(summary = "提交本部门现员基础表至人事科", description = "考勤管理员将本部门的现员基础表提交给劳动人事科。")
    @PostMapping("/basic/submit")
    public ApiResponse<Void> submitBasicTable() {
        staffLedgerService.submitBasicTable();
        return ApiResponse.success("现员基础表提交成功", null);
    }

    @Operation(summary = "各部门现员基础表提交状态", description = "劳动人事科查看所有部门的现员基础表提交情况，支持按状态筛选和分页。")
    @GetMapping("/basic/submissions")
    public ApiResponse<PageResponse<Map<String, Object>>> getBasicTableSubmissions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(staffLedgerService.getBasicTableSubmissions(status, pageNum, pageSize));
    }

    @Operation(summary = "待审核台账列表", description = "查询台账列表，不传状态则返回全部，支持分页。")
    @GetMapping("/pending")
    public ApiResponse<PageResponse<LedgerPendingResponse>> getPendingLedgers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(staffLedgerService.getPendingLedgers(status, pageNum, pageSize));
    }

    @Operation(summary = "所有台账列表(超级管理员)")
    @GetMapping("/all")
    public ApiResponse<PageResponse<LedgerPendingResponse>> getAllLedgers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String month,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(staffLedgerService.getAllLedgers(status, month, pageNum, pageSize));
    }

    @Operation(summary = "获取台账配置")
    @GetMapping("/config")
    public ApiResponse<Map<String, String>> getLedgerConfig() {
        return ApiResponse.success(staffLedgerService.getLedgerConfig());
    }

    @Operation(summary = "更新台账配置")
    @PutMapping("/config")
    public ApiResponse<Void> updateLedgerConfig(@Valid @RequestBody List<LedgerConfigRequest> requests) {
        staffLedgerService.updateLedgerConfig(requests);
        return ApiResponse.success("配置更新成功", null);
    }

    @Operation(summary = "获取全部考勤管理员", description = "返回所有已启用的考勤管理员列表及其所属部门。")
    @GetMapping("/attendance-admins")
    public ApiResponse<List<AttendanceAdminResponse>> getAllAttendanceAdmins() {
        return ApiResponse.success(staffLedgerService.getAllAttendanceAdmins());
    }

    // ==================== 动态路径接口（/{id} 放在最后） ====================

    @Operation(summary = "获取台账详情")
    @GetMapping("/{id}")
    public ApiResponse<LedgerResponse> getLedger(@PathVariable Long id) {
        return ApiResponse.success(staffLedgerService.getLedgerById(id));
    }

    @Operation(summary = "保存台账明细", description = "考勤管理员填写岗点后保存台账。")
    @PutMapping("/{id}/details")
    public ApiResponse<LedgerResponse> saveLedgerDetails(@PathVariable Long id, @Valid @RequestBody SaveLedgerRequest request) {
        return ApiResponse.success("保存成功", staffLedgerService.saveLedgerDetails(id, request));
    }

    @Operation(summary = "提交台账", description = "考勤管理员提交台账给车间主任审批。")
    @PostMapping("/{id}/submit")
    public ApiResponse<LedgerResponse> submitLedger(@PathVariable Long id) {
        return ApiResponse.success("提交成功", staffLedgerService.submitLedger(id));
    }

    @Operation(summary = "提交现员表至人事科", description = "考勤管理员将本部门当月现员表提交给劳动人事科审核。")
    @PostMapping("/submit-to-hr")
    public ApiResponse<LedgerResponse> submitLedgerToHr(@RequestParam(required = false) String month) {
        return ApiResponse.success("提交成功", staffLedgerService.submitLedgerToHr(month));
    }

    @Operation(summary = "主任审批台账", description = "车间主任审批台账，可同意或退回。")
    @PostMapping("/{id}/approve")
    public ApiResponse<LedgerResponse> approveLedger(@PathVariable Long id, @Valid @RequestBody ApproveLedgerRequest request) {
        return ApiResponse.success("审批完成", staffLedgerService.approveLedger(id, request));
    }

    @Operation(summary = "人事科审核台账", description = "劳动人事科审核台账，可同意或拒绝。")
    @PostMapping("/{id}/hr-review")
    public ApiResponse<LedgerResponse> hrReviewLedger(@PathVariable Long id, @Valid @RequestBody ApproveLedgerRequest request) {
        return ApiResponse.success("审核完成", staffLedgerService.hrReviewLedger(id, request));
    }

    @Operation(summary = "导出台账PDF")
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> exportLedgerPdf(@PathVariable Long id) {
        try {
            byte[] data = ledgerExportService.exportLedgerToPdf(id);
            String filename = URLEncoder.encode("现员台账.pdf", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.APPLICATION_PDF).body(data);
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    @Operation(summary = "导出台账Excel")
    @GetMapping("/{id}/excel")
    public ResponseEntity<byte[]> exportLedgerExcel(@PathVariable Long id) {
        try {
            byte[] data = ledgerExportService.exportLedgerToExcel(id);
            String filename = URLEncoder.encode("现员台账.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(data);
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    @Operation(summary = "导出按班别分列的现员分布台账Excel", description = "按日勤/甲班/乙班/丙班/丁班/预备分列展示员工姓名。")
    @GetMapping("/{id}/distribution-excel")
    public ResponseEntity<byte[]> exportLedgerDistributionExcel(@PathVariable Long id) {
        try {
            byte[] data = ledgerExportService.exportLedgerDistributionExcel(id);
            String filename = URLEncoder.encode("现员分布台账.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(data);
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    @Operation(summary = "月度数据对比")
    @GetMapping("/{id}/compare")
    public ApiResponse<LedgerMonthCompareResponse> compareWithPreviousMonth(@PathVariable Long id) {
        return ApiResponse.success(staffLedgerService.compareWithPreviousMonth(id));
    }
}
