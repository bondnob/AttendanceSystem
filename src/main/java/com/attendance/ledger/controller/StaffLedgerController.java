package com.attendance.ledger.controller;

import com.attendance.auth.security.CurrentUser;
import com.attendance.auth.security.UserContext;
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
import com.attendance.ledger.dto.ShareLedgerRequest;
import com.attendance.ledger.dto.SubmitLedgerRequest;
import com.attendance.ledger.dto.TemplateFieldsResponse;
import com.attendance.ledger.dto.UpdateEmployeeBasicRequest;
import com.attendance.ledger.mapper.StaffLedgerMapper;
import com.attendance.ledger.model.StaffLedger;
import com.attendance.ledger.service.LedgerExportService;
import com.attendance.ledger.service.StaffLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@Slf4j
@RequestMapping("/api/ledger")
public class StaffLedgerController {

    private final StaffLedgerService staffLedgerService;
    private final LedgerExportService ledgerExportService;
    private final StaffLedgerMapper staffLedgerMapper;

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

    @Operation(summary = "获取本部门现员基础表", description = "考勤管理员获取本部门已下发的现员基础表，超级管理员返回全部。传orgUnitId则按指定部门查询。支持筛选非在岗和即将退休人员。")
    @GetMapping("/basic/my")
    public ApiResponse<PageResponse<EmployeeBasicResponse>> getMyEmployeeBasic(
            @RequestParam(required = false) Long orgUnitId,
            @RequestParam(required = false) String categoryMajor,
            @RequestParam(required = false) Integer retirementAge,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(staffLedgerService.getMyEmployeeBasic(orgUnitId, categoryMajor, retirementAge, pageNum, pageSize));
    }

    @Operation(summary = "修改现员基础表", description = "考勤管理员修改现员基础表中的工种、部门班组、劳动班制、班组长。")
    @PutMapping("/basic/update")
    public ApiResponse<EmployeeBasicResponse> updateEmployeeBasic(@Valid @RequestBody UpdateEmployeeBasicRequest request) {
        return ApiResponse.success("修改成功", staffLedgerService.updateEmployeeBasic(request));
    }

    @Operation(summary = "导出现员基础表(Excel)", description = "支持按人员类别(categoryMajor)和退休年龄(retirementAge)筛选导出。")
    @GetMapping("/basic/export")
    public ResponseEntity<byte[]> exportEmployeeBasic(
            @RequestParam(required = false) Long orgUnitId,
            @RequestParam(required = false) String categoryMajor,
            @RequestParam(required = false) Integer retirementAge) {
        try {
            byte[] data = ledgerExportService.exportEmployeeBasicToExcel(orgUnitId, categoryMajor, retirementAge);
            String filename = URLEncoder.encode("现员基础表.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    @Operation(summary = "批量导出多部门现员基础表(Excel)", description = "劳动人事科按选中的部门ID数组批量导出，每个部门一个Sheet。")
    @PostMapping("/basic/batch-export")
    public ResponseEntity<byte[]> batchExportEmployeeBasic(@RequestBody Map<String, List<Long>> body) {
        try {
            byte[] data = ledgerExportService.batchExportEmployeeBasicToExcel(body.get("orgUnitIds"));
            String filename = URLEncoder.encode("现员基础表（批量）.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    @Operation(summary = "批量导出多部门现员台账(Excel)", description = "劳动人事科按选中的部门ID数组批量导出，每个部门一个Sheet。month不传默认当月。")
    @PostMapping("/batch-export")
    public ResponseEntity<byte[]> batchExportLedger(@RequestBody Map<String, Object> body,
                                                    @RequestParam(required = false) String month) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> orgUnitIds = ((List<Number>) body.get("orgUnitIds")).stream().map(Number::longValue).collect(Collectors.toList());
            String effectiveMonth = month != null ? month : (body.get("month") != null ? body.get("month").toString() : null);
            byte[] data = ledgerExportService.batchExportLedgerToExcel(orgUnitIds, effectiveMonth);
            String filename = URLEncoder.encode("现员台账（批量）.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
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

    @Operation(summary = "按模板导出本部门台账Excel", description = "自动根据当前用户所在部门查找台账并按车间模板导出。")
    @GetMapping("/my/template-excel")
    public ResponseEntity<byte[]> exportMyLedgerTemplateExcel(@RequestParam(required = false) String month) {
        try {
            CurrentUser currentUser = UserContext.get();
            if (currentUser == null || currentUser.getOrgUnitId() == null)
                return ResponseEntity.status(401).build();
            String effectiveMonth = month != null ? month : LocalDate.now().toString().substring(0, 7);
            StaffLedger ledger = staffLedgerMapper.findByOrgUnitAndMonth(currentUser.getOrgUnitId(), effectiveMonth);
            if (ledger == null)
                return ResponseEntity.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("当前部门本月台账不存在，请先同步台账".getBytes(StandardCharsets.UTF_8));
            byte[] data = ledgerExportService.fillTemplateExcel(ledger.getId());
            String filename = URLEncoder.encode("现员分布台账_模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) {
            log.error("按模板导出本部门台账Excel失败", e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
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

    @Operation(summary = "获取可共享的领导列表", description = "返回劳动人事科科长、正职领导、副职领导、党委书记。")
    @GetMapping("/leaders")
    public ApiResponse<List<AttendanceAdminResponse>> getShareableLeaders() {
        return ApiResponse.success(staffLedgerService.getShareableLeaders());
    }

    // ==================== 台账共享相关 ====================

    @Operation(summary = "共享台账给领导", description = "劳动人事科将台账共享给指定领导，支持单选和多选。")
    @PostMapping("/share")
    public ApiResponse<Void> shareLedger(@Valid @RequestBody ShareLedgerRequest request) {
        staffLedgerService.shareLedger(request);
        return ApiResponse.success("共享成功", null);
    }

    @Operation(summary = "查看我被共享的台账列表", description = "领导查看自己被共享的所有台账，支持按状态筛选和分页。")
    @GetMapping("/shared-with-me")
    public ApiResponse<PageResponse<LedgerPendingResponse>> getSharedLedgers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return ApiResponse.success(staffLedgerService.getSharedLedgersForLeader(status, pageNum, pageSize));
    }

    @Operation(summary = "撤销台账共享", description = "劳动人事科撤销对指定领导的台账共享。")
    @DeleteMapping("/share")
    public ApiResponse<Void> revokeSharing(@RequestParam Long ledgerId, @RequestParam Long targetUserId) {
        staffLedgerService.revokeSharing(ledgerId, targetUserId);
        return ApiResponse.success("撤销成功", null);
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

    @Operation(summary = "提交台账", description = "考勤管理员提交台账给车间主任审批，可同时提交在岗人数、备注和变动说明。")
    @PostMapping("/submit")
    public ApiResponse<LedgerResponse> submitLedger(@RequestBody(required = false) SubmitLedgerRequest request) {
        return ApiResponse.success("提交成功", staffLedgerService.submitLedger(request));
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
        } catch (Exception e) {
            log.error("导出台账Excel失败, ledgerId={}", id, e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
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

    @Operation(summary = "获取车间台账模板字段", description = "根据车间模板返回该车间需要的字段列表（不同车间班次列不同）。")
    @GetMapping("/template-fields/{orgUnitId}")
    public ApiResponse<TemplateFieldsResponse> getTemplateFields(@PathVariable Long orgUnitId) {
        return ApiResponse.success(ledgerExportService.getTemplateFields(orgUnitId));
    }

    @Operation(summary = "下载车间台账模板")
    @GetMapping("/template/download/{orgUnitId}")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable Long orgUnitId) {
        try {
            byte[] data = ledgerExportService.downloadTemplate(orgUnitId);
            String filename = URLEncoder.encode("台账模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    @Operation(summary = "导入台账数据", description = "按照台账模板填写好的Excel上传，系统解析数据并写入台账明细。")
    @PostMapping("/template/upload/{orgUnitId}")
    public ApiResponse<Void> importLedgerData(@PathVariable Long orgUnitId,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false) String month) {
        staffLedgerService.importLedgerData(orgUnitId, file, month);
        return ApiResponse.success("台账数据导入成功", null);
    }

    @Operation(summary = "按模板导出台账Excel", description = "使用各车间专属模板填充数据后导出，保留原始模板格式。")
    @GetMapping("/{id}/template-excel")
    public ResponseEntity<byte[]> exportLedgerTemplateExcel(@PathVariable Long id) {
        try {
            byte[] data = ledgerExportService.fillTemplateExcel(id);
            String filename = URLEncoder.encode("现员分布台账_模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(data);
        } catch (Exception e) {
            log.error("按模板导出台账Excel失败, ledgerId={}", id, e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    @Operation(summary = "按模板导出台账PDF", description = "按照各车间台账模板的表头结构生成PDF。")
    @GetMapping("/{id}/template-pdf")
    public ResponseEntity<byte[]> exportLedgerTemplatePdf(@PathVariable Long id) {
        try {
            byte[] data = ledgerExportService.exportLedgerTemplatePdf(id);
            String filename = URLEncoder.encode("现员分布台账.pdf", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                    .contentType(MediaType.APPLICATION_PDF).body(data);
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    @Operation(summary = "月度数据对比")
    @GetMapping("/{id}/compare")
    public ApiResponse<LedgerMonthCompareResponse> compareWithPreviousMonth(@PathVariable Long id) {
        return ApiResponse.success(staffLedgerService.compareWithPreviousMonth(id));
    }
}
