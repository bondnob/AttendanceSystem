package com.attendance.leave.service;

import com.attendance.admin.mapper.OrgUnitMapper;
import com.attendance.admin.model.OrgUnit;
import com.attendance.exception.BizException;
import com.attendance.leave.dto.ApprovalRecordResponse;
import com.attendance.leave.dto.LeaveDetailResponse;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveDocumentService {

    private static final String APPLICANT_TYPE_EMPLOYEE = "EMPLOYEE";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String EMPLOYEE_TEMPLATE = "docs/职工请假记录单.pdf";
    private static final String EMPLOYEE_PERSONAL_OVER_30_TEMPLATE = "docs/职工请假记录单事假三十天以上.pdf";
    private static final String CADRE_TEMPLATE = "docs/管理人员请假记录单.pdf";
    private static final float DECISION_FONT_SIZE = 16f;
    private static final float SIGNATURE_WIDTH = 72f;
    private static final float SIGNATURE_HEIGHT = 56f;
    private static final float SIGNATURE_Y_OFFSET = -3f;
    private static final float SIGNATURE_X_OFFSET = 82f;
    private static final BigDecimal DAY_10 = BigDecimal.TEN;
    private static final BigDecimal DAY_30 = BigDecimal.valueOf(30);

    @Value("${attendance.file-storage-path:uploads}")
    private String fileStoragePath;

    private final OrgUnitMapper orgUnitMapper;

    public String generatePdf(Long leaveId, LeaveDetailResponse detail) {
        if (detail.getFinalApprovedAt() == null) {
            throw new BizException("请假单尚未完成，不能生成 PDF");
        }
        try {
            Path directory = Paths.get(fileStoragePath, "leave-pdfs");
            Files.createDirectories(directory);
            deleteExistingPdfs(directory, leaveId);
            Path target = directory.resolve(buildPdfFileName(leaveId, detail.getFinalApprovedAt()));
            Path templatePath = resolveTemplatePath(detail);
            try (PdfReader reader = new PdfReader(templatePath.toString());
                 OutputStream outputStream = Files.newOutputStream(target)) {
                Document document = new Document(reader.getPageSizeWithRotation(1));
                PdfWriter writer = PdfWriter.getInstance(document, outputStream);
                document.open();
                PdfContentByte canvas = writer.getDirectContent();
                PdfImportedPage templatePage = writer.getImportedPage(reader, 1);
                canvas.addTemplate(templatePage, 0, 0);
                writeOverlay(canvas, detail);
                document.close();
            }
            return "/files/leave-pdfs/" + target.getFileName();
        } catch (IOException | DocumentException ex) {
            throw new BizException("请假单 PDF 生成失败");
        }
    }

    public String resolveExistingPdfUrl(Long leaveId) {
        Path directory = Paths.get(fileStoragePath, "leave-pdfs");
        if (!Files.exists(directory)) {
            return null;
        }
        try {
            return Files.list(directory)
                    .filter(path -> path.getFileName().toString().startsWith("leave_" + leaveId + "_"))
                    .sorted((left, right) -> right.getFileName().toString().compareTo(left.getFileName().toString()))
                    .map(path -> "/files/leave-pdfs/" + path.getFileName())
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }

    private void writeOverlay(PdfContentByte canvas, LeaveDetailResponse detail) throws IOException, DocumentException {
        BaseFont font = loadChineseBaseFont();
        if (!usesEmployeePdfLayout(detail)) {
            writeCadreOverlay(canvas, font, detail);
            return;
        }
        writeText(canvas, font, 12, resolveOrgName(detail.getOrgUnitId()), 226, 666);
        writeCenteredText(canvas, font, 10.5f, safe(detail.getJobTitleSnapshot()), 67, 121, 632);
        writeCenteredText(canvas, font, 10.5f, safe(detail.getApplicantName()), 121, 175, 632);
        writeCenteredText(canvas, font, 10.5f, safe(detail.getLeaveTypeName()), 175, 229, 632);
        writeCenteredText(canvas, font, 10.5f, formatDateRange(detail.getStartTime(), detail.getEndTime()), 229, 402, 632);
        writeCenteredText(canvas, font, 10.5f, formatDays(detail.getLeaveDays()), 402, 471, 632);
        writeCenteredText(canvas, font, 10.5f, safe(detail.getRemark()), 471, 528, 632);
        writeMultilineText(canvas, font, 11, safe(detail.getReason()), 128, 611, 390, 15);
        writeText(canvas, font, 11, safe(detail.getApplicantName()), 356, 554);
        writeDateSplit(canvas, font, detail.getSubmittedAt(), 410, 454, 478, 554);

        List<ApprovalSlot> slots = resolveApprovalSlots(detail);
        for (ApprovalSlot slot : slots) {
            writeApprovalSlot(canvas, font, slot);
        }
    }

    private void writeCadreOverlay(PdfContentByte canvas, BaseFont font, LeaveDetailResponse detail)
            throws IOException, DocumentException {
        writeText(canvas, font, 12, resolveOrgName(detail.getOrgUnitId()), 196, 666);
        writeCenteredText(canvas, font, 10.5f, safe(detail.getJobTitleSnapshot()), 67, 121, 632);
        writeCenteredText(canvas, font, 10.5f, safe(detail.getApplicantName()), 121, 175, 632);
        writeCenteredText(canvas, font, 10.5f, safe(detail.getLeaveTypeName()), 175, 229, 632);
        writeCenteredText(canvas, font, 10.5f, formatDateRange(detail.getStartTime(), detail.getEndTime()), 229, 402, 632);
        writeCenteredText(canvas, font, 10.5f, formatDays(detail.getLeaveDays()), 402, 471, 632);
        writeCenteredText(canvas, font, 10.5f, safe(detail.getRemark()), 471, 528, 632);
        writeMultilineText(canvas, font, 11, safe(detail.getReason()), 128, 611, 390, 15);
        writeText(canvas, font, 11, safe(detail.getApplicantName()), 356, 554);
        writeDateSplit(canvas, font, detail.getSubmittedAt(), 410, 454, 478, 554);

        List<ApprovalSlot> slots = resolveApprovalSlots(detail);
        for (ApprovalSlot slot : slots) {
            writeApprovalSlot(canvas, font, slot);
        }
    }

    private void writeApprovalSlot(PdfContentByte canvas, BaseFont font, ApprovalSlot slot) throws IOException, DocumentException {
        if (slot.content() != null && !slot.content().isBlank()) {
            writeMultilineText(canvas, font, 11, slot.content(), slot.left() + 8, slot.contentTop(), slot.width(), 15);
        }
        if (slot.contentDate() != null) {
            writeDateSplit(canvas, font, slot.contentDate(), slot.dateYearX(), slot.dateMonthX(), slot.dateDayX(), slot.dateY());
        }
        if (slot.approval() == null) {
            return;
        }
        Image signature = loadSignatureImage(slot.approval().getSignatureUrl());
        if (signature != null) {
            signature.scaleAbsolute(SIGNATURE_WIDTH, SIGNATURE_HEIGHT);
            float signatureX = slot.left() + SIGNATURE_X_OFFSET;
            float signatureY = slot.decisionY() - ((SIGNATURE_HEIGHT - DECISION_FONT_SIZE) / 2f) + SIGNATURE_Y_OFFSET;
            signature.setAbsolutePosition(signatureX, signatureY);
            PdfGState gState = new PdfGState();
            gState.setFillOpacity(1f);
            canvas.saveState();
            canvas.setGState(gState);
            canvas.addImage(signature);
            canvas.restoreState();
        }
        writeDateSplit(canvas, font, slot.approval().getApprovedAt(),
                slot.dateYearX(),
                slot.dateMonthX(),
                slot.dateDayX(),
                slot.dateY());
    }

    private void writeText(PdfContentByte canvas, BaseFont font, float size, String text, float x, float y) {
        if (text == null || text.isBlank()) {
            return;
        }
        canvas.beginText();
        canvas.setFontAndSize(font, size);
        canvas.setTextMatrix(x, y);
        canvas.showText(text);
        canvas.endText();
    }

    private void writeCenteredText(PdfContentByte canvas, BaseFont font, float size, String text, float left, float right, float y) {
        if (text == null || text.isBlank()) {
            return;
        }
        float textWidth = font.getWidthPoint(text, size);
        float x = left + Math.max(0, ((right - left) - textWidth) / 2);
        writeText(canvas, font, size, text, x, y);
    }

    private void writeMultilineText(PdfContentByte canvas, BaseFont font, float size, String text,
                                    float left, float top, float width, float leading) throws DocumentException {
        if (text == null || text.isBlank()) {
            return;
        }
        ColumnText columnText = new ColumnText(canvas);
        columnText.setSimpleColumn(left, top - 80, left + width, top);
        columnText.setLeading(leading);
        columnText.setText(new com.lowagie.text.Phrase(text, new com.lowagie.text.Font(font, size)));
        columnText.go();
    }

    private void writeDateSplit(PdfContentByte canvas, BaseFont font, LocalDateTime time,
                                float yearX, float monthX, float dayX, float y) {
        if (time == null) {
            return;
        }
        writeText(canvas, font, 12, String.valueOf(time.getYear()), yearX, y);
        writeText(canvas, font, 12, String.valueOf(time.getMonthValue()), monthX, y);
        writeText(canvas, font, 12, String.valueOf(time.getDayOfMonth()), dayX, y);
    }

    private Path resolveTemplatePath(LeaveDetailResponse detail) {
        String relativePath;
        if (usesEmployeePdfLayout(detail)) {
            relativePath = isPersonalLeaveOver30Days(detail) ? EMPLOYEE_PERSONAL_OVER_30_TEMPLATE : EMPLOYEE_TEMPLATE;
        } else {
            relativePath = CADRE_TEMPLATE;
        }
        Path template = findProjectFile(relativePath);
        if (template == null) {
            throw new BizException("请假单模板不存在，查找路径: " + buildLookupMessage(relativePath));
        }
        return template;
    }

    private boolean usesEmployeePdfLayout(LeaveDetailResponse detail) {
        return APPLICANT_TYPE_EMPLOYEE.equals(detail.getApplicantType());
    }

    private Path findProjectFile(String relativePath) {
        for (Path baseDir : collectLookupBaseDirs()) {
            Path current = baseDir;
            while (current != null) {
                Path candidate = current.resolve(relativePath).normalize();
                if (Files.exists(candidate)) {
                    return candidate;
                }
                current = current.getParent();
            }
        }
        return null;
    }

    private String buildLookupMessage(String relativePath) {
        List<String> candidates = new ArrayList<>();
        for (Path baseDir : collectLookupBaseDirs()) {
            Path current = baseDir;
            while (current != null) {
                candidates.add(current.resolve(relativePath).normalize().toString());
                current = current.getParent();
            }
        }
        return String.join(" | ", candidates);
    }

    private List<Path> collectLookupBaseDirs() {
        Set<Path> baseDirs = new LinkedHashSet<>();
        baseDirs.add(Paths.get("").toAbsolutePath().normalize());
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            baseDirs.add(Paths.get(userDir).toAbsolutePath().normalize());
        }
        try {
            Path codeSource = Paths.get(LeaveDocumentService.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()).toAbsolutePath().normalize();
            baseDirs.add(codeSource);
            if (Files.isRegularFile(codeSource)) {
                Path parent = codeSource.getParent();
                if (parent != null) {
                    baseDirs.add(parent);
                }
            }
        } catch (URISyntaxException | NullPointerException ignored) {
        }
        return new ArrayList<>(baseDirs);
    }

    private Image loadSignatureImage(String signatureUrl) {
        String newSignatureUrl = normalizeSignatureUrl(signatureUrl);
        log.info("loadSignatureImage: " + newSignatureUrl);
        if (newSignatureUrl == null || newSignatureUrl.isBlank()) {
            return null;
        }
        try {
            Path path = resolveSignaturePath(newSignatureUrl);
            if (path == null) {
                log.warn("signature image not found: {}", newSignatureUrl);
                return null;
            }
            return Image.getInstance(path.toAbsolutePath().toString());
        } catch (Exception ex) {
            log.warn("load signature image failed: {}", newSignatureUrl, ex);
            return null;
        }
    }

    private String normalizeSignatureUrl(String signatureUrl) {
        if (signatureUrl == null || signatureUrl.isBlank() || "undefined".equalsIgnoreCase(signatureUrl.trim())) {
            return null;
        }
        return signatureUrl.trim().replace("http://121.41.90.50", "");
    }

    private Path resolveSignaturePath(String signatureUrl) {
        String normalized = extractPath(signatureUrl);
        if (normalized.startsWith("/files/")) {
            normalized = normalized.substring("/files/".length());
        } else if (normalized.startsWith("files/")) {
            normalized = normalized.substring("files/".length());
        } else if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.replace("\\", "/");

        List<Path> candidates = new ArrayList<>();
        Path directPath = Paths.get(normalized);
        if (directPath.isAbsolute()) {
            candidates.add(directPath);
        }
        candidates.add(Paths.get(fileStoragePath).resolve(normalized));
        for (Path baseDir : collectLookupBaseDirs()) {
            candidates.add(baseDir.resolve(fileStoragePath).resolve(normalized));
            candidates.add(baseDir.resolve(normalized));
        }

        for (Path candidate : candidates) {
            Path path = candidate.normalize();
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    private String extractPath(String url) {
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                return URI.create(trimmed).getPath();
            } catch (IllegalArgumentException ignored) {
            }
        }
        return trimmed;
    }

    private BaseFont loadChineseBaseFont() throws IOException, DocumentException {
        String[] candidates = new String[]{
                "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc,0",
                "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/wqy-microhei/wqy-microhei.ttc",

                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
                "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc,0",

                "C:/Windows/Fonts/simsun.ttc,0",
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/msyh.ttc,0"
        };

        for (String candidate : candidates) {
            try {
                BaseFont font = BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                log.info("use pdf chinese font: {}", candidate);
                return font;
            } catch (Exception ignored) {
            }
        }

        try {
            BaseFont font = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            log.info("use pdf chinese font: STSong-Light");
            return font;
        } catch (Exception ex) {
            throw new BizException("未找到可用中文字体，无法生成 PDF");
        }
    }

    private void deleteExistingPdfs(Path directory, Long leaveId) throws IOException {
        if (leaveId == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().startsWith("leave_" + leaveId + "_"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            log.warn("delete existing leave pdf failed: {}", path, ex);
                        }
                    });
        }
    }

    private String resolveOrgName(Long orgUnitId) {
        if (orgUnitId == null) {
            return "";
        }
        OrgUnit orgUnit = orgUnitMapper.findById(orgUnitId);
        return orgUnit == null ? "" : safe(orgUnit.getOrgName());
    }

    private List<ApprovalSlot> resolveApprovalSlots(LeaveDetailResponse detail) {
        List<ApprovalRecordResponse> approvals = detail.getApprovals() == null ? List.of() : detail.getApprovals();
        List<ApprovalSlot> slots = new ArrayList<>();
        if (usesEmployeePdfLayout(detail)) {
            ApprovalRecordResponse unitLeader = findApprovalByRole(approvals, "UNIT_LEADER");
            ApprovalRecordResponse orgPrincipal = findApprovalByRole(approvals, "ORG_PRINCIPAL");
            if (isPersonalLeaveOver30Days(detail)) {
                slots.add(new ApprovalSlot(67, 541, 295, 463, "", null, orgPrincipal, 176, 220, 244, 467, 505));
                slots.add(new ApprovalSlot(296, 541, 528, 463, "", null, findApprovalByRole(approvals, "HR_SECTION_CHIEF"), 410, 454, 478, 467, 505));
            } else {
                slots.add(new ApprovalSlot(67, 541, 295, 463, safe(detail.getTeamLeaderSnapshot()), detail.getSubmittedAt(), null, 176, 220, 244, 467, 0));
                slots.add(new ApprovalSlot(296, 541, 528, 463, "", null, orgPrincipal, 410, 454, 478, 467, 505));
            }
            if (shouldPlaceUnitLeaderInStationmasterSlot(detail) && unitLeader != null) {
                slots.add(new ApprovalSlot(67, 463, 295, 384, "", null, unitLeader, 176, 220, 244, 388, 426));
                slots.add(new ApprovalSlot(296, 463, 528, 384, "", null, findApprovalByRole(approvals, "HR_SECTION_CHIEF"), 410, 454, 478, 388, 426));
            } else {
                slots.add(new ApprovalSlot(67, 463, 295, 384, "", null, findApprovalByRole(approvals, "HR_SECTION_CHIEF"), 176, 220, 244, 388, 426));
                slots.add(new ApprovalSlot(296, 463, 528, 384, "", null, findLastLeaderApproval(approvals), 410, 454, 478, 388, 426));
            }
            return slots;
        }
        ApprovalRecordResponse orgPrincipal = findApprovalByRole(approvals, "ORG_PRINCIPAL");
        ApprovalRecordResponse second = approvals.size() > 1 ? approvals.get(1) : null;
        ApprovalRecordResponse stationmaster = findApprovalByRoleOrName(approvals, "STATIONMASTER", "站长");
        ApprovalRecordResponse partySecretary = findApprovalByRoleOrName(approvals, "PARTY_SECRETARY", "党委书记");
        slots.add(new ApprovalSlot(67, 541, 295, 463, "", null, orgPrincipal, 176, 220, 244, 467, 505));
        slots.add(new ApprovalSlot(296, 541, 528, 463, "", null, second, 410, 454, 478, 467, 505));
        slots.add(new ApprovalSlot(67, 463, 295, 384, "", null, stationmaster, 176, 220, 244, 388, 426));
        slots.add(new ApprovalSlot(296, 463, 528, 384, "", null, partySecretary, 410, 454, 478, 388, 426));
        return slots;
    }

    private ApprovalRecordResponse findApprovalByRole(List<ApprovalRecordResponse> approvals, String roleCode) {
        return approvals.stream()
                .filter(item -> roleCode.equals(item.getApproverRoleCode()))
                .findFirst()
                .orElse(null);
    }

    private ApprovalRecordResponse findApprovalByRoleOrName(List<ApprovalRecordResponse> approvals,
                                                            String roleCode,
                                                            String roleName) {
        return approvals.stream()
                .filter(item -> roleCode.equals(item.getApproverRoleCode())
                        || roleName.equals(item.getApproverRoleName()))
                .findFirst()
                .orElse(null);
    }

    private boolean shouldPlaceUnitLeaderInStationmasterSlot(LeaveDetailResponse detail) {
        return isSickLeaveOver30Days(detail) || isPersonalLeaveOver10AndWithin30Days(detail);
    }

    private boolean isSickLeaveOver30Days(LeaveDetailResponse detail) {
        return isLeaveType(detail, "病") && isGreaterThan(detail.getLeaveDays(), DAY_30);
    }

    private boolean isPersonalLeaveOver30Days(LeaveDetailResponse detail) {
        return isLeaveType(detail, "事") && isGreaterThan(detail.getLeaveDays(), DAY_30);
    }

    private boolean isPersonalLeaveOver10AndWithin30Days(LeaveDetailResponse detail) {
        BigDecimal leaveDays = detail.getLeaveDays();
        return isLeaveType(detail, "事")
                && leaveDays != null
                && leaveDays.compareTo(DAY_10) > 0
                && leaveDays.compareTo(DAY_30) <= 0;
    }

    private boolean isLeaveType(LeaveDetailResponse detail, String leaveTypeKeyword) {
        return detail.getLeaveTypeName() != null && detail.getLeaveTypeName().contains(leaveTypeKeyword);
    }

    private boolean isGreaterThan(BigDecimal value, BigDecimal target) {
        return value != null && value.compareTo(target) > 0;
    }

    private ApprovalRecordResponse findLastLeaderApproval(List<ApprovalRecordResponse> approvals) {
        return approvals.stream()
                .filter(item -> !"ORG_PRINCIPAL".equals(item.getApproverRoleCode()))
                .filter(item -> !"HR_SECTION_CHIEF".equals(item.getApproverRoleCode()))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private String buildPdfFileName(Long leaveId, LocalDateTime approvedAt) {
        return "leave_" + leaveId + "_" + approvedAt.format(FILE_TIME_FORMATTER)
                + "_" + LocalDateTime.now().format(FILE_TIME_FORMATTER) + ".pdf";
    }

    private String formatDateRange(LocalDateTime startTime, LocalDateTime endTime) {
        return formatDate(startTime) + " - " + formatDate(endTime);
    }

    private String formatDate(LocalDateTime time) {
        return time == null ? "" : time.format(DATE_FORMATTER);
    }

    private String formatDays(BigDecimal leaveDays) {
        return leaveDays == null ? "" : leaveDays.stripTrailingZeros().toPlainString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ApprovalSlot(float left, float top, float right, float bottom,
                                String content, LocalDateTime contentDate, ApprovalRecordResponse approval,
                                float dateYearX, float dateMonthX, float dateDayX, float dateY,
                                float decisionY) {
        float width() {
            return right - left - 20;
        }

        float contentTop() {
            return top - 14;
        }
    }
}
