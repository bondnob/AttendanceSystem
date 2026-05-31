package com.attendance.ledger.service;

import com.attendance.admin.mapper.OrgUnitMapper;
import com.attendance.admin.mapper.TeamNameMapper;
import com.attendance.admin.model.OrgUnit;
import com.attendance.admin.model.TeamName;
import com.attendance.auth.security.CurrentUser;
import com.attendance.auth.security.UserContext;
import com.attendance.common.PageResponse;
import com.attendance.exception.BizException;
import com.attendance.ledger.dto.ApproveLedgerRequest;
import com.attendance.ledger.dto.AttendanceAdminResponse;
import com.attendance.ledger.dto.EmployeeBasicResponse;
import com.attendance.ledger.dto.LedgerApprovalRecordResponse;
import com.attendance.ledger.dto.LedgerConfigRequest;
import com.attendance.ledger.dto.LedgerDetailResponse;
import com.attendance.ledger.dto.LedgerMonthCompareResponse;
import com.attendance.ledger.dto.LedgerPendingResponse;
import com.attendance.ledger.dto.LedgerResponse;
import com.attendance.ledger.dto.SaveLedgerDetailRequest;
import com.attendance.ledger.dto.SaveLedgerRequest;
import com.attendance.ledger.dto.UpdateEmployeeBasicRequest;
import com.attendance.ledger.mapper.EmployeeBasicMapper;
import com.attendance.ledger.mapper.EmployeeBasicSubmissionMapper;
import com.attendance.ledger.mapper.LedgerApprovalRecordMapper;
import com.attendance.ledger.mapper.LedgerConfigMapper;
import com.attendance.ledger.mapper.StaffLedgerDetailMapper;
import com.attendance.ledger.mapper.StaffLedgerMapper;
import com.attendance.ledger.model.EmployeeBasic;
import com.attendance.ledger.model.EmployeeBasicSubmission;
import com.attendance.ledger.model.LedgerApprovalRecord;
import com.attendance.ledger.model.LedgerConfig;
import com.attendance.ledger.model.StaffLedger;
import com.attendance.ledger.model.StaffLedgerDetail;
import com.attendance.leave.enums.RoleCode;
import com.attendance.leave.mapper.UserAccountMapper;
import com.attendance.leave.model.UserAccount;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffLedgerService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_DIRECTOR_APPROVED = "DIRECTOR_APPROVED";
    private static final String STATUS_RETURNED = "RETURNED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String ACTION_APPROVE = "APPROVE";
    private static final String ACTION_RETURN = "RETURN";
    private static final String ACTION_REJECT = "REJECT";
    private static final String STEP_DIRECTOR = "DIRECTOR";
    private static final String STEP_HR = "HR";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final EmployeeBasicMapper employeeBasicMapper;
    private final EmployeeBasicSubmissionMapper employeeBasicSubmissionMapper;
    private final StaffLedgerMapper staffLedgerMapper;
    private final StaffLedgerDetailMapper staffLedgerDetailMapper;
    private final LedgerApprovalRecordMapper ledgerApprovalRecordMapper;
    private final LedgerConfigMapper ledgerConfigMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final TeamNameMapper teamNameMapper;
    private final UserAccountMapper userAccountMapper;

    // ==================== 现员基础表相关 ====================

    @Transactional
    public Map<String, Object> uploadEmployeeBasic(MultipartFile file) {
        requireSystemAdmin();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            String uploadBatch = "BATCH_" + System.currentTimeMillis();

            // 预加载所有组织单元，构建名称->ID映射（精确匹配 + 模糊匹配）
            List<OrgUnit> allOrgUnits = orgUnitMapper.findAll();
            Map<String, Long> exactNameMap = new HashMap<>();
            for (OrgUnit org : allOrgUnits) {
                exactNameMap.put(org.getOrgName().trim().replaceAll("\\s+", ""), org.getId());
            }

            int imported = 0, updated = 0, skipped = 0;
            List<String> errors = new ArrayList<>();
            List<Map<String, Object>> unmatchedRecords = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String idCardNo = getCellStringValue(row, 0);

                String empName = getCellStringValue(row, 1);
                String gender = getCellStringValue(row, 2);
                String birthDate = getCellStringValue(row, 3);
                String workType = getCellStringValue(row, 4);
                String identityType = getCellStringValue(row, 5);
                String categoryMajor = getCellStringValue(row, 6);
                String categoryMinor = getCellStringValue(row, 7);
                Integer age = getCellIntValue(row, 8);
                String laborShift = getCellStringValue(row, 9);
                String teamLeaderStr = getCellStringValue(row, 10);
                String orgName = getCellStringValue(row, 11);
                String teamName = getCellStringValue(row, 12);

                if (age == null && birthDate != null && !birthDate.isBlank()) {
                    try {
                        String yearStr = birthDate.length() >= 4 ? birthDate.substring(0, 4) : null;
                        if (yearStr != null) age = LocalDate.now().getYear() - Integer.parseInt(yearStr);
                    } catch (NumberFormatException ignored) {}
                }

                // 根据科室车间名称获取orgUnitId：先精确匹配，再模糊匹配
                Long orgUnitId = resolveOrgUnitId(orgName, exactNameMap, allOrgUnits);

                // 班组长：正班组长、副班组长、不是班组长
                String isTeamLeader = "否";
                if (teamLeaderStr != null) {
                    String trimmed = teamLeaderStr.trim();
                    if ("正班组长".equals(trimmed) || "正".equals(trimmed)) {
                        isTeamLeader = "正班组长";
                    } else if ("副班组长".equals(trimmed) || "副".equals(trimmed)) {
                        isTeamLeader = "副班组长";
                    } else if ("是".equals(trimmed) || "1".equals(trimmed) || "班组长".equals(trimmed)) {
                        isTeamLeader = "正班组长";  // 兼容旧数据
                    }
                }

                // org_unit_id 为 NOT NULL 且有外键约束，匹配不到时记录到未匹配列表
                if (orgUnitId == null) {
                    Map<String, Object> unmatched = new HashMap<>();
                    unmatched.put("row", i + 1);
                    unmatched.put("idCardNo", idCardNo);
                    unmatched.put("empName", empName);
                    unmatched.put("orgName", orgName);
                    unmatchedRecords.add(unmatched);
                    skipped++;
                    continue;
                }

                EmployeeBasic existing = idCardNo != null && !idCardNo.isBlank() ? employeeBasicMapper.findByIdCardNo(idCardNo) : null;
                if (existing != null) {
                    existing.setEmpName(empName); existing.setGender(gender); existing.setBirthDate(birthDate);

                    existing.setWorkType(workType); existing.setIdentityType(identityType);
                    existing.setCategoryMajor(categoryMajor); existing.setCategoryMinor(categoryMinor);
                    existing.setAge(age); existing.setLaborShift(laborShift); existing.setIsTeamLeader(isTeamLeader);
                    existing.setOrgUnitId(orgUnitId); existing.setTeamName(teamName); existing.setIsActive(1);
                    existing.setUploadBatch(uploadBatch);
                    employeeBasicMapper.updateByIdCardNo(existing);
                    updated++;
                } else {
                    EmployeeBasic emp = new EmployeeBasic();
                    emp.setIdCardNo(idCardNo); emp.setEmpName(empName); emp.setGender(gender);
                    emp.setBirthDate(birthDate); emp.setWorkType(workType); emp.setIdentityType(identityType);
                    emp.setCategoryMajor(categoryMajor); emp.setCategoryMinor(categoryMinor);
                    emp.setAge(age); emp.setLaborShift(laborShift); emp.setIsTeamLeader(isTeamLeader);
                    emp.setOrgUnitId(orgUnitId); emp.setTeamName(teamName); emp.setIsActive(1);
                    emp.setUploadBatch(uploadBatch);
                    employeeBasicMapper.insert(emp);
                    imported++;
                }
            }

            if (imported == 0 && updated == 0 && unmatchedRecords.isEmpty()) {
                String detail = errors.isEmpty() ? "Excel文件中没有有效数据" : String.join("；", errors);
                throw new BizException("导入失败：" + detail);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("uploadBatch", uploadBatch);
            result.put("imported", imported); result.put("updated", updated); result.put("skipped", skipped);
            if (!errors.isEmpty()) result.put("errors", errors);
            if (!unmatchedRecords.isEmpty()) {
                result.put("unmatchedCount", unmatchedRecords.size());
                result.put("unmatchedRecords", unmatchedRecords);
            }
            return result;
        } catch (BizException e) { throw e; } catch (Exception e) { throw new BizException("Excel导入失败: " + e.getMessage()); }
    }

    @Transactional
    public void distributeToUsers(List<Long> userIds) {
        requireSystemAdmin();
        for (Long userId : userIds) {
            UserAccount user = userAccountMapper.findById(userId);
            if (user == null) throw new BizException("用户不存在: " + userId);
            if (user.getOrgUnitId() == null) throw new BizException("用户未绑定组织: " + userId);
            employeeBasicMapper.distributeByOrgUnitId(user.getOrgUnitId(), LocalDateTime.now());
        }
    }

    public PageResponse<EmployeeBasicResponse> getMyEmployeeBasic(Long orgUnitId, Integer pageNum, Integer pageSize) {
        CurrentUser currentUser = requireLogin();
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 500);
        int offset = (safePageNum - 1) * safePageSize;

        Long queryOrgId = orgUnitId != null ? orgUnitId : currentUser.getOrgUnitId();

        long total;
        List<EmployeeBasic> list;
        if (isSystemAdmin(currentUser) && orgUnitId == null) {
            total = employeeBasicMapper.countAll();
            list = employeeBasicMapper.findAllWithPage(offset, safePageSize);
        } else {
            total = employeeBasicMapper.countByOrgUnitId(queryOrgId);
            list = employeeBasicMapper.findByOrgUnitIdWithPage(queryOrgId, offset, safePageSize);
        }

        List<EmployeeBasicResponse> records = list.stream().map(this::toEmployeeBasicResponse).collect(Collectors.toList());
        return PageResponse.<EmployeeBasicResponse>builder()
                .total(total).pageNum(safePageNum).pageSize(safePageSize).records(records).build();
    }

    @Transactional
    public EmployeeBasicResponse updateEmployeeBasic(UpdateEmployeeBasicRequest request) {
        CurrentUser currentUser = requireLogin();
        EmployeeBasic emp = employeeBasicMapper.findById(request.getId());
        if (emp == null) throw new BizException("员工不存在");
        if (!emp.getOrgUnitId().equals(currentUser.getOrgUnitId())) throw new BizException("无权修改该员工信息");
        if (emp.getIsDistributed() == null || emp.getIsDistributed() != 1) throw new BizException("该员工数据未下发，不能修改");

        emp.setWorkType(request.getWorkType());
        emp.setTeamName(request.getTeamName());
        emp.setLaborShift(request.getLaborShift());
        emp.setIsTeamLeader(request.getIsTeamLeader());
        employeeBasicMapper.updateEditableFields(emp);
        return toEmployeeBasicResponse(employeeBasicMapper.findById(emp.getId()));
    }

    // ==================== 现员台账相关 ====================

    @Transactional
    public LedgerResponse syncToLedger(String month) {
        CurrentUser currentUser = requireLogin();
        Long orgUnitId = currentUser.getOrgUnitId();
        String effectiveMonth = month != null ? month : LocalDate.now().format(MONTH_FMT);

        StaffLedger ledger = staffLedgerMapper.findByOrgUnitAndMonth(orgUnitId, effectiveMonth);
        if (ledger == null) {
            ledger = new StaffLedger();
            ledger.setOrgUnitId(orgUnitId);
            ledger.setLedgerMonth(effectiveMonth);
            ledger.setStatus(STATUS_DRAFT);
            ledger.setCreatedBy(currentUser.getUserId());
            staffLedgerMapper.insert(ledger);
        }

        staffLedgerDetailMapper.deleteByLedgerId(ledger.getId());
        ledgerApprovalRecordMapper.deleteByLedgerId(ledger.getId());
        ledger.setStatus(STATUS_DRAFT);
        ledger.setDirectorUserId(null); ledger.setDirectorOpinion(null); ledger.setDirectorApprovedAt(null);
        ledger.setHrUserId(null); ledger.setHrOpinion(null); ledger.setHrApprovedAt(null);
        ledger.setSubmittedAt(null);
        ledger.setInWorkCount(null); ledger.setRemark(null); ledger.setChangeDescription(null);
        staffLedgerMapper.resetForSync(ledger);

        List<EmployeeBasic> employees = employeeBasicMapper.findDistributedByOrgUnitId(orgUnitId);
        List<StaffLedgerDetail> details = new ArrayList<>();
        int sortNo = 0;
        for (EmployeeBasic emp : employees) {
            StaffLedgerDetail detail = new StaffLedgerDetail();
            detail.setLedgerId(ledger.getId());
            detail.setEmployeeBasicId(emp.getId());
            detail.setTeamName(emp.getTeamName());
            detail.setWorkType(emp.getWorkType());
            // 根据班组名称查找班别
            if (emp.getTeamName() != null && !emp.getTeamName().isBlank()) {
                TeamName tn = teamNameMapper.findByOrgUnitIdAndTeamName(orgUnitId, emp.getTeamName());
                detail.setShiftCategory(tn != null ? tn.getShiftCategory() : null);
            }
            detail.setSortNo(sortNo++);
            details.add(detail);
        }
        if (!details.isEmpty()) staffLedgerDetailMapper.batchInsert(details);

        List<EmployeeBasic> nonWorking = employeeBasicMapper.findNonWorkingByOrgUnitId(orgUnitId);
        int workCount = employees.size() - nonWorking.size();
        ledger.setInWorkCount(workCount);

        StringBuilder remark = new StringBuilder();
        List<EmployeeBasic> nearRetirement = employeeBasicMapper.findNearRetirementByOrgUnitId(orgUnitId);
        if (!nearRetirement.isEmpty()) {
            remark.append("即将退休人员：");
            for (EmployeeBasic emp : nearRetirement) {
                remark.append(emp.getEmpName()).append("(").append(emp.getBirthDate()).append(")、");
            }
            remark.setLength(remark.length() - 1);
        }
        ledger.setRemark(remark.toString());
        staffLedgerMapper.updateStatusAndCounts(ledger);

        return buildLedgerResponse(ledger);
    }

    public LedgerResponse getMyLedger(String month) {
        CurrentUser currentUser = requireLogin();
        String effectiveMonth = month != null ? month : LocalDate.now().format(MONTH_FMT);
        StaffLedger ledger = staffLedgerMapper.findByOrgUnitAndMonth(currentUser.getOrgUnitId(), effectiveMonth);
        return ledger != null ? buildLedgerResponse(ledger) : null;
    }

    public LedgerResponse getLedgerById(Long ledgerId) {
        requireLogin();
        return buildLedgerResponse(requireLedger(ledgerId));
    }

    @Transactional
    public LedgerResponse saveLedgerDetails(Long ledgerId, SaveLedgerRequest request) {
        CurrentUser currentUser = requireLogin();
        StaffLedger ledger = requireLedger(ledgerId);
        if (!STATUS_DRAFT.equals(ledger.getStatus()) && !STATUS_RETURNED.equals(ledger.getStatus()))
            throw new BizException("当前状态不允许修改");
        if (!ledger.getOrgUnitId().equals(currentUser.getOrgUnitId()) && !isSystemAdmin(currentUser))
            throw new BizException("无权修改该台账");

        for (SaveLedgerDetailRequest detailReq : request.getDetails()) {
            StaffLedgerDetail detail = staffLedgerDetailMapper.findById(detailReq.getId());
            if (detail == null || !detail.getLedgerId().equals(ledgerId)) throw new BizException("明细记录不存在");
            detail.setStationPoint(detailReq.getStationPoint());
            detail.setTeamName(detailReq.getTeamName());
            detail.setShiftCategory(detailReq.getShiftCategory());
            detail.setWorkType(detailReq.getWorkType());
            detail.setSortNo(detailReq.getSortNo());
            staffLedgerDetailMapper.update(detail);
        }

        if (request.getInWorkCount() != null) ledger.setInWorkCount(request.getInWorkCount());
        if (request.getRemark() != null) ledger.setRemark(request.getRemark());
        if (request.getChangeDescription() != null) ledger.setChangeDescription(request.getChangeDescription());
        staffLedgerMapper.updateStatusAndCounts(ledger);
        return buildLedgerResponse(ledger);
    }

    @Transactional
    public LedgerResponse submitLedger(Long ledgerId) {
        CurrentUser currentUser = requireLogin();
        StaffLedger ledger = requireLedger(ledgerId);
        if (!STATUS_DRAFT.equals(ledger.getStatus()) && !STATUS_RETURNED.equals(ledger.getStatus()))
            throw new BizException("当前状态不允许提交");
        if (!ledger.getOrgUnitId().equals(currentUser.getOrgUnitId()) && !isSystemAdmin(currentUser))
            throw new BizException("无权提交该台账");

        ledger.setStatus(STATUS_SUBMITTED);
        ledger.setSubmittedAt(LocalDateTime.now());
        staffLedgerMapper.updateSubmitted(ledger);
        saveApprovalRecord(ledgerId, STEP_DIRECTOR, "SUBMIT", null, currentUser.getUserId());
        return buildLedgerResponse(ledger);
    }

    @Transactional
    public LedgerResponse submitLedgerToHr(String month) {
        CurrentUser currentUser = requireLogin();
        String effectiveMonth = month != null ? month : LocalDate.now().format(MONTH_FMT);
        StaffLedger ledger = staffLedgerMapper.findByOrgUnitAndMonth(currentUser.getOrgUnitId(), effectiveMonth);
        if (ledger == null) throw new BizException("当前部门本月台账不存在，请先同步");
        if (!STATUS_DRAFT.equals(ledger.getStatus()) && !STATUS_RETURNED.equals(ledger.getStatus()))
            throw new BizException("当前状态不允许提交");

        ledger.setStatus(STATUS_DIRECTOR_APPROVED);
        ledger.setSubmittedAt(LocalDateTime.now());
        staffLedgerMapper.updateSubmitted(ledger);
        saveApprovalRecord(ledger.getId(), STEP_DIRECTOR, ACTION_APPROVE, "考勤管理员直接提交至人事科", currentUser.getUserId());
        return buildLedgerResponse(ledger);
    }

    @Transactional
    public void submitBasicTable() {
        CurrentUser currentUser = requireLogin();
        Long orgUnitId = currentUser.getOrgUnitId();

        long count = employeeBasicMapper.countByOrgUnitId(orgUnitId);
        if (count == 0) throw new BizException("当前部门没有现员基础表数据");

        EmployeeBasicSubmission submission = new EmployeeBasicSubmission();
        submission.setOrgUnitId(orgUnitId);
        submission.setStatus("SUBMITTED");
        submission.setSubmittedBy(currentUser.getUserId());
        submission.setSubmittedAt(LocalDateTime.now());
        employeeBasicSubmissionMapper.insert(submission);
    }

    public PageResponse<Map<String, Object>> getBasicTableSubmissions(String status, Integer pageNum, Integer pageSize) {
        requireLogin();
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);

        List<OrgUnit> allOrg = orgUnitMapper.findAll();
        List<Map<String, Object>> all = allOrg.stream().map(org -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orgUnitId", org.getId());
            item.put("orgUnitName", org.getOrgName());
            EmployeeBasicSubmission sub = employeeBasicSubmissionMapper.findLatestByOrgUnitId(org.getId());
            item.put("status", sub != null ? sub.getStatus() : "NOT_SUBMITTED");
            item.put("submittedAt", sub != null ? sub.getSubmittedAt() : null);
            long employeeCount = employeeBasicMapper.countByOrgUnitId(org.getId());
            item.put("employeeCount", employeeCount);
            return item;
        }).collect(Collectors.toList());

        if (status != null && !status.isBlank()) {
            String upper = status.trim().toUpperCase();
            all = all.stream()
                    .filter(item -> upper.equals(item.get("status")))
                    .collect(Collectors.toList());
        }

        long total = all.size();
        int fromIndex = (safePageNum - 1) * safePageSize;
        int toIndex = Math.min(fromIndex + safePageSize, all.size());
        List<Map<String, Object>> records = fromIndex < all.size() ? all.subList(fromIndex, toIndex) : List.of();

        return PageResponse.<Map<String, Object>>builder()
                .total(total).pageNum(safePageNum).pageSize(safePageSize).records(records)
                .build();
    }

    @Transactional
    public LedgerResponse approveLedger(Long ledgerId, ApproveLedgerRequest request) {
        CurrentUser currentUser = requireLogin();
        StaffLedger ledger = requireLedger(ledgerId);
        if (!STATUS_SUBMITTED.equals(ledger.getStatus())) throw new BizException("当前状态不允许审批");

        ledger.setDirectorUserId(currentUser.getUserId());
        ledger.setDirectorOpinion(request.getOpinion());
        ledger.setDirectorApprovedAt(LocalDateTime.now());
        ledger.setStatus(ACTION_APPROVE.equals(request.getAction()) ? STATUS_DIRECTOR_APPROVED : STATUS_RETURNED);
        staffLedgerMapper.updateDirectorApproval(ledger);
        saveApprovalRecord(ledgerId, STEP_DIRECTOR, request.getAction(), request.getOpinion(), currentUser.getUserId());
        return buildLedgerResponse(ledger);
    }

    @Transactional
    public LedgerResponse hrReviewLedger(Long ledgerId, ApproveLedgerRequest request) {
        CurrentUser currentUser = requireLogin();
        StaffLedger ledger = requireLedger(ledgerId);
        if (!STATUS_DIRECTOR_APPROVED.equals(ledger.getStatus())) throw new BizException("当前状态不允许审核");
        if (!isHrAdmin(currentUser) && !isSystemAdmin(currentUser)) throw new BizException("只有劳动人事科可以审核");

        ledger.setHrUserId(currentUser.getUserId());
        ledger.setHrOpinion(request.getOpinion());
        ledger.setHrApprovedAt(LocalDateTime.now());
        ledger.setStatus(ACTION_APPROVE.equals(request.getAction()) ? STATUS_APPROVED : STATUS_REJECTED);
        staffLedgerMapper.updateHrReview(ledger);
        saveApprovalRecord(ledgerId, STEP_HR, request.getAction(), request.getOpinion(), currentUser.getUserId());
        return buildLedgerResponse(ledger);
    }

    public PageResponse<LedgerPendingResponse> getPendingLedgers(String status, Integer pageNum, Integer pageSize) {
        requireLogin();
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int offset = (safePageNum - 1) * safePageSize;
        Long total = staffLedgerMapper.countByCondition(status);
        List<StaffLedger> ledgers = staffLedgerMapper.findPageByCondition(status, offset, safePageSize);
        List<LedgerPendingResponse> records = ledgers.stream().map(l -> {
            OrgUnit org = orgUnitMapper.findById(l.getOrgUnitId());
            UserAccount creator = userAccountMapper.findById(l.getCreatedBy());
            return LedgerPendingResponse.builder()
                    .id(l.getId()).orgUnitId(l.getOrgUnitId())
                    .orgUnitName(org != null ? org.getOrgName() : "")
                    .ledgerMonth(l.getLedgerMonth()).status(l.getStatus())
                    .inWorkCount(l.getInWorkCount())
                    .creatorName(creator != null ? creator.getEmpName() : "")
                    .submittedAt(l.getSubmittedAt()).updatedAt(l.getUpdatedAt())
                    .build();
        }).collect(Collectors.toList());
        return PageResponse.<LedgerPendingResponse>builder()
                .total(total == null ? 0L : total)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .records(records)
                .build();
    }

    public PageResponse<LedgerPendingResponse> getAllLedgers(String status, String month, Integer pageNum, Integer pageSize) {
        requireSystemAdmin();
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        List<StaffLedger> allLedgers = staffLedgerMapper.findByCondition(status, month);
        int total = allLedgers.size();
        int fromIndex = (safePageNum - 1) * safePageSize;
        int toIndex = Math.min(fromIndex + safePageSize, total);
        List<LedgerPendingResponse> data = new ArrayList<>();
        if (fromIndex < total) {
            data = allLedgers.subList(fromIndex, toIndex).stream().map(l -> {
                OrgUnit org = orgUnitMapper.findById(l.getOrgUnitId());
                UserAccount creator = userAccountMapper.findById(l.getCreatedBy());
                return LedgerPendingResponse.builder()
                        .id(l.getId()).orgUnitId(l.getOrgUnitId())
                        .orgUnitName(org != null ? org.getOrgName() : "")
                        .ledgerMonth(l.getLedgerMonth()).status(l.getStatus())
                        .inWorkCount(l.getInWorkCount())
                        .creatorName(creator != null ? creator.getEmpName() : "")
                        .submittedAt(l.getSubmittedAt()).updatedAt(l.getUpdatedAt())
                        .build();
            }).collect(Collectors.toList());
        }
        return PageResponse.<LedgerPendingResponse>builder().total((long) total).pageNum(safePageNum).pageSize(safePageSize).records(data).build();
    }

    public LedgerMonthCompareResponse compareWithPreviousMonth(Long ledgerId) {
        requireLogin();
        StaffLedger currentLedger = requireLedger(ledgerId);
        YearMonth current = YearMonth.parse(currentLedger.getLedgerMonth(), MONTH_FMT);
        String previousMonth = current.minusMonths(1).format(MONTH_FMT);
        StaffLedger previousLedger = staffLedgerMapper.findByOrgUnitAndMonth(currentLedger.getOrgUnitId(), previousMonth);

        List<LedgerMonthCompareResponse.CompareItem> differences = new ArrayList<>();
        List<StaffLedgerDetail> currentDetails = staffLedgerDetailMapper.findByLedgerId(ledgerId);
        Map<Long, StaffLedgerDetail> currentMap = currentDetails.stream()
                .collect(Collectors.toMap(StaffLedgerDetail::getEmployeeBasicId, d -> d));

        if (previousLedger != null) {
            List<StaffLedgerDetail> previousDetails = staffLedgerDetailMapper.findByLedgerId(previousLedger.getId());
            Map<Long, StaffLedgerDetail> previousMap = previousDetails.stream()
                    .collect(Collectors.toMap(StaffLedgerDetail::getEmployeeBasicId, d -> d));

            for (StaffLedgerDetail curr : currentDetails) {
                EmployeeBasic emp = employeeBasicMapper.findById(curr.getEmployeeBasicId());
                if (emp == null) continue;
                StaffLedgerDetail prev = previousMap.get(curr.getEmployeeBasicId());
                if (prev == null) {
                    differences.add(LedgerMonthCompareResponse.CompareItem.builder()
                            .empName(emp.getEmpName()).idCardNo(emp.getIdCardNo())
                            .field("新增人员").previousValue("-").currentValue(emp.getEmpName()).changeType("ADDED").build());
                    continue;
                }
                addDiffIfChanged(differences, emp, "岗点", prev.getStationPoint(), curr.getStationPoint());
            }
            for (StaffLedgerDetail prev : previousDetails) {
                if (!currentMap.containsKey(prev.getEmployeeBasicId())) {
                    EmployeeBasic emp = employeeBasicMapper.findById(prev.getEmployeeBasicId());
                    if (emp != null) {
                        differences.add(LedgerMonthCompareResponse.CompareItem.builder()
                                .empName(emp.getEmpName()).idCardNo(emp.getIdCardNo())
                                .field("减少人员").previousValue(emp.getEmpName()).currentValue("-").changeType("REMOVED").build());
                    }
                }
            }
        }
        return LedgerMonthCompareResponse.builder().currentMonth(currentLedger.getLedgerMonth()).previousMonth(previousMonth).differences(differences).build();
    }

    public Map<String, String> getLedgerConfig() {
        return ledgerConfigMapper.findAll().stream()
                .collect(Collectors.toMap(LedgerConfig::getConfigKey, LedgerConfig::getConfigValue, (a, b) -> b));
    }

    @Transactional
    public void updateLedgerConfig(List<LedgerConfigRequest> requests) {
        requireSystemAdmin();
        for (LedgerConfigRequest req : requests) ledgerConfigMapper.updateValue(req.getConfigKey(), req.getConfigValue());
    }

    public List<AttendanceAdminResponse> getAllAttendanceAdmins() {
        requireLogin();
        List<UserAccount> admins = userAccountMapper.findEnabledByRole(RoleCode.ATTENDANCE_ADMIN);
        return admins.stream().map(a -> {
            OrgUnit org = orgUnitMapper.findById(a.getOrgUnitId());
            return AttendanceAdminResponse.builder()
                    .userId(a.getId())
                    .empName(a.getEmpName())
                    .orgUnitId(a.getOrgUnitId())
                    .orgUnitName(org != null ? org.getOrgName() : "")
                    .build();
        }).collect(Collectors.toList());
    }

    // ==================== 辅助方法 ====================

    private LedgerResponse buildLedgerResponse(StaffLedger ledger) {
        OrgUnit orgUnit = orgUnitMapper.findById(ledger.getOrgUnitId());
        UserAccount creator = userAccountMapper.findById(ledger.getCreatedBy());
        UserAccount director = ledger.getDirectorUserId() != null ? userAccountMapper.findById(ledger.getDirectorUserId()) : null;
        UserAccount hr = ledger.getHrUserId() != null ? userAccountMapper.findById(ledger.getHrUserId()) : null;

        List<StaffLedgerDetail> details = staffLedgerDetailMapper.findByLedgerId(ledger.getId());
        List<LedgerDetailResponse> detailResponses = new ArrayList<>();
        List<LedgerDetailResponse> nonWorkingResponses = new ArrayList<>();

        for (StaffLedgerDetail detail : details) {
            EmployeeBasic emp = employeeBasicMapper.findById(detail.getEmployeeBasicId());
            if (emp == null) continue;
            LedgerDetailResponse resp = LedgerDetailResponse.builder()
                    .id(detail.getId()).employeeBasicId(detail.getEmployeeBasicId())
                    .idCardNo(emp.getIdCardNo()).empName(emp.getEmpName()).gender(emp.getGender())
                    .birthDate(emp.getBirthDate()).age(emp.getAge())
                    .workType(detail.getWorkType() != null ? detail.getWorkType() : emp.getWorkType())
                    .identityType(emp.getIdentityType()).categoryMajor(emp.getCategoryMajor())
                    .categoryMinor(emp.getCategoryMinor()).laborShift(emp.getLaborShift())
                    .teamName(detail.getTeamName() != null ? detail.getTeamName() : emp.getTeamName())
                    .shiftCategory(detail.getShiftCategory())
                    .stationPoint(detail.getStationPoint())
                    .shiftType(emp.getLaborShift()).isTeamLeader(emp.getIsTeamLeader())
                    .isNonWorking(emp.getIsActive() == 0 ? 1 : 0)
                    .nonWorkingReason(emp.getIsActive() == 0 ? emp.getCategoryMajor() : null)
                    .sortNo(detail.getSortNo()).build();
            if (emp.getIsActive() == 0) nonWorkingResponses.add(resp); else detailResponses.add(resp);
        }

        List<LedgerApprovalRecord> records = ledgerApprovalRecordMapper.findByLedgerId(ledger.getId());
        return LedgerResponse.builder()
                .id(ledger.getId()).orgUnitId(ledger.getOrgUnitId())
                .orgUnitName(orgUnit != null ? orgUnit.getOrgName() : "")
                .ledgerMonth(ledger.getLedgerMonth()).status(ledger.getStatus())
                .inWorkCount(ledger.getInWorkCount()).remark(ledger.getRemark())
                .changeDescription(ledger.getChangeDescription())
                .directorUserId(ledger.getDirectorUserId()).directorName(director != null ? director.getEmpName() : null)
                .directorOpinion(ledger.getDirectorOpinion()).directorApprovedAt(ledger.getDirectorApprovedAt())
                .hrUserId(ledger.getHrUserId()).hrName(hr != null ? hr.getEmpName() : null)
                .hrOpinion(ledger.getHrOpinion()).hrApprovedAt(ledger.getHrApprovedAt())
                .submittedAt(ledger.getSubmittedAt()).createdBy(ledger.getCreatedBy())
                .creatorName(creator != null ? creator.getEmpName() : "")
                .createdAt(ledger.getCreatedAt()).updatedAt(ledger.getUpdatedAt())
                .details(detailResponses).nonWorkingDetails(nonWorkingResponses)
                .approvalRecords(records.stream().map(r -> {
                    UserAccount op = userAccountMapper.findById(r.getOperatorUserId());
                    return LedgerApprovalRecordResponse.builder()
                            .id(r.getId()).step(r.getStep()).action(r.getAction()).opinion(r.getOpinion())
                            .operatorUserId(r.getOperatorUserId()).operatorName(op != null ? op.getEmpName() : "")
                            .createdAt(r.getCreatedAt()).build();
                }).collect(Collectors.toList()))
                .config(getLedgerConfig()).build();
    }

    private EmployeeBasicResponse toEmployeeBasicResponse(EmployeeBasic emp) {
        OrgUnit org = orgUnitMapper.findById(emp.getOrgUnitId());
        return EmployeeBasicResponse.builder()
                .id(emp.getId()).idCardNo(emp.getIdCardNo()).empName(emp.getEmpName())
                .gender(emp.getGender()).birthDate(emp.getBirthDate()).age(emp.getAge())
                .workType(emp.getWorkType()).identityType(emp.getIdentityType())
                .categoryMajor(emp.getCategoryMajor()).categoryMinor(emp.getCategoryMinor())
                .laborShift(emp.getLaborShift()).isTeamLeader(emp.getIsTeamLeader())
                .orgUnitId(emp.getOrgUnitId()).orgUnitName(org != null ? org.getOrgName() : "")
                .teamName(emp.getTeamName()).isActive(emp.getIsActive())
                .isDistributed(emp.getIsDistributed()).distributedAt(emp.getDistributedAt()).build();
    }

    private void addDiffIfChanged(List<LedgerMonthCompareResponse.CompareItem> differences, EmployeeBasic emp, String field, String prev, String curr) {
        String p = prev != null ? prev : ""; String c = curr != null ? curr : "";
        if (!p.equals(c)) {
            differences.add(LedgerMonthCompareResponse.CompareItem.builder()
                    .empName(emp.getEmpName()).idCardNo(emp.getIdCardNo()).field(field)
                    .previousValue(p.isEmpty() ? "-" : p).currentValue(c.isEmpty() ? "-" : c).changeType("CHANGED").build());
        }
    }

    private void saveApprovalRecord(Long ledgerId, String step, String action, String opinion, Long operatorId) {
        LedgerApprovalRecord record = new LedgerApprovalRecord();
        record.setLedgerId(ledgerId); record.setStep(step); record.setAction(action);
        record.setOpinion(opinion); record.setOperatorUserId(operatorId);
        ledgerApprovalRecordMapper.insert(record);
    }

    private Long resolveOrgUnitId(String orgName, Map<String, Long> exactNameMap, List<OrgUnit> allOrgUnits) {
        if (orgName == null || orgName.isBlank()) return null;
        String normalized = orgName.trim().replaceAll("\\s+", "");
        // 1. 精确匹配
        Long exactId = exactNameMap.get(normalized);
        if (exactId != null) return exactId;
        // 2. 模糊匹配：Excel名称包含在数据库名称中，或数据库名称包含在Excel名称中
        for (OrgUnit org : allOrgUnits) {
            String dbName = org.getOrgName().trim().replaceAll("\\s+", "");
            if (dbName.contains(normalized) || normalized.contains(dbName)) {
                return org.getId();
            }
        }
        return null;
    }

    private String getCellStringValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }

    private Integer getCellIntValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return (int) cell.getNumericCellValue();
        if (cell.getCellType() == CellType.STRING) { try { return Integer.parseInt(cell.getStringCellValue().trim()); } catch (NumberFormatException e) { return null; } }
        return null;
    }

    private StaffLedger requireLedger(Long ledgerId) {
        StaffLedger ledger = staffLedgerMapper.findById(ledgerId);
        if (ledger == null) throw new BizException("台账不存在");
        return ledger;
    }

    private CurrentUser requireLogin() {
        CurrentUser currentUser = UserContext.get();
        if (currentUser == null) throw new BizException("请先登录");
        return currentUser;
    }

    private boolean isSystemAdmin(CurrentUser user) { return user != null && "SYSTEM_ADMIN".equals(user.getRoleCode()); }

    private boolean isHrAdmin(CurrentUser user) {
        if (user == null) return false;
        UserAccount account = userAccountMapper.findById(user.getUserId());
        return account != null && (RoleCode.HR_SECTION_CHIEF.equals(account.getRoleCode())
                || "ATTENDANCE_ADMIN".equals(account.getRoleCode()) && account.getOrgUnitId() != null && isHrOrg(account.getOrgUnitId()));
    }

    private boolean isHrOrg(Long orgUnitId) {
        OrgUnit org = orgUnitMapper.findById(orgUnitId);
        return org != null && org.getOrgName().contains("劳动人事");
    }

    private void requireSystemAdmin() {
        CurrentUser currentUser = UserContext.get();
        if (currentUser == null || !"SYSTEM_ADMIN".equals(currentUser.getRoleCode())) throw new BizException("只有超级管理员可以执行该操作");
    }
}
