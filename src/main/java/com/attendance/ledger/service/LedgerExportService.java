package com.attendance.ledger.service;

import com.attendance.admin.mapper.OrgUnitMapper;
import com.attendance.admin.model.OrgUnit;
import com.attendance.exception.BizException;
import com.attendance.ledger.dto.TemplateFieldsResponse;
import com.attendance.ledger.dto.TemplateFieldsResponse.FieldItem;
import com.attendance.ledger.mapper.EmployeeBasicMapper;
import com.attendance.ledger.mapper.StaffLedgerDetailMapper;
import com.attendance.ledger.mapper.StaffLedgerMapper;
import com.attendance.ledger.model.EmployeeBasic;
import com.attendance.ledger.model.StaffLedger;
import com.attendance.ledger.model.StaffLedgerDetail;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Table;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerExportService {

    private final EmployeeBasicMapper employeeBasicMapper;
    private final StaffLedgerMapper staffLedgerMapper;
    private final StaffLedgerDetailMapper staffLedgerDetailMapper;
    private final OrgUnitMapper orgUnitMapper;

    @Value("${ledger.template.dir:docs/ledger-templates}")
    private String templateDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, String> STANDARD_SHIFT_MAP = new LinkedHashMap<>();
    static {
        STANDARD_SHIFT_MAP.put("甲班", "jiaBan");
        STANDARD_SHIFT_MAP.put("乙班", "yiBan");
        STANDARD_SHIFT_MAP.put("丙班", "bingBan");
        STANDARD_SHIFT_MAP.put("丁班", "dingBan");
        STANDARD_SHIFT_MAP.put("预备", "yuBei");
        STANDARD_SHIFT_MAP.put("半班", "yuBei");
    }

    private File findTemplateFile(String orgName) {
        File dir = new File(templateDir);
        if (!dir.exists() || !dir.isDirectory()) throw new BizException("台账模板目录不存在: " + templateDir);
        Map<String, String> nameMapping = Map.of("设备检修车间", "设备维修车间");
        String searchName = nameMapping.getOrDefault(orgName, orgName);
        for (File f : dir.listFiles((d, n) -> n.endsWith(".xlsx") && !n.startsWith("~"))) {
            if (f.getName().contains(searchName)) return f;
        }
        throw new BizException("未找到对应的台账模板文件，车间名称: " + orgName);
    }

    public byte[] downloadTemplate(Long orgUnitId) throws IOException {
        OrgUnit orgUnit = orgUnitMapper.findById(orgUnitId);
        if (orgUnit == null) throw new BizException("组织单位不存在");
        File tplFile = findTemplateFile(orgUnit.getOrgName());
        return Files.readAllBytes(tplFile.toPath());
    }

    public void uploadTemplate(Long orgUnitId, MultipartFile file) {
        OrgUnit orgUnit = orgUnitMapper.findById(orgUnitId);
        if (orgUnit == null) throw new BizException("组织单位不存在");
        if (file == null || file.isEmpty()) throw new BizException("上传文件不能为空");
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.endsWith(".xlsx")) throw new BizException("只支持 .xlsx 格式");
        File dir = new File(templateDir);
        if (!dir.exists()) dir.mkdirs();
        File oldFile = null;
        try { oldFile = findTemplateFile(orgUnit.getOrgName()); } catch (Exception ignored) {}
        if (oldFile != null && oldFile.exists()) oldFile.delete();
        File dest = new File(dir, originalName);
        try (FileOutputStream out = new FileOutputStream(dest)) { out.write(file.getBytes()); }
        catch (IOException e) { throw new BizException("保存模板文件失败: " + e.getMessage()); }
    }

    private Map<String, String[]> parseExtraShiftJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, String[]> result = new HashMap<>();
            raw.forEach((k, v) -> {
                if (v instanceof List) {
                    // 后端标准格式：["姓名1", "姓名2"]
                    List<String> list = (List<String>) v;
                    result.put(k, new String[]{
                        list.size() > 0 ? list.get(0) : "", list.size() > 1 ? list.get(1) : ""
                    });
                } else if (v instanceof String) {
                    // 前端格式：单个字符串 "姓名"
                    result.put(k, new String[]{(String) v, ""});
                }
            });
            return result;
        } catch (Exception e) { return new HashMap<>(); }
    }

    /** 填充模板Excel — 直接在原模板上填充数据，不改动模板格式 */
    public byte[] fillTemplateExcel(Long ledgerId) throws IOException {
        StaffLedger ledger = staffLedgerMapper.findById(ledgerId);
        if (ledger == null) throw new BizException("台账不存在");
        OrgUnit orgUnit = orgUnitMapper.findById(ledger.getOrgUnitId());
        if (orgUnit == null) throw new BizException("组织单位不存在");
        File tplFile = findTemplateFile(orgUnit.getOrgName());
        try (Workbook tplWb = new XSSFWorkbook(new FileInputStream(tplFile))) {
            Sheet sheet = tplWb.getSheetAt(0);
            fillSheetFromTemplate(tplWb, sheet, ledger);
            // 第二行写入当月日期并合并至与表格等宽
            fillDateRow(tplWb, sheet, ledger);
            // 合并"班别"到"班制"之间的单元格
            mergeBanBieRow(sheet);
            // 全部填完后统一加内外边框
            addBorders(tplWb, sheet);
            // 日期行左对齐（addBorders统一居中后再单独设置）
            setDateLeftAlign(tplWb, sheet);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            tplWb.write(out);
            return out.toByteArray();
        }
    }

    private void fillDateRow(Workbook wb, Sheet sheet, StaffLedger ledger) {
        int lastCol = 0;
        for (int r = 2; r <= 4; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            if (row.getLastCellNum() - 1 > lastCol) lastCol = row.getLastCellNum() - 1;
        }
        if (lastCol <= 1) return;
        // 移除第二行已有的合并区域
        for (int i = sheet.getNumMergedRegions() - 1; i >= 0; i--) {
            org.apache.poi.ss.util.CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() == 1 && region.getLastRow() == 1) {
                sheet.removeMergedRegion(i);
            }
        }
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, lastCol));
        // 写入日期
        String dateText = "日期：";
        String month = ledger.getLedgerMonth();
        if (month != null && !month.isBlank()) {
            try {
                String[] parts = month.split("-");
                dateText += Integer.parseInt(parts[0]) + "年" + Integer.parseInt(parts[1]) + "月";
            } catch (Exception e) { dateText += month; }
        }
        Row row1 = sheet.getRow(1);
        if (row1 == null) row1 = sheet.createRow(1);
        Cell cell = row1.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellValue(dateText);
    }

    private void mergeBanBieRow(Sheet sheet) {
        Row row3 = sheet.getRow(2);
        if (row3 == null) return;
        int banBieCol = -1, banZhiCol = -1;
        for (int i = 0; i <= 25; i++) {
            Cell c = row3.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null || c.getCellType() != CellType.STRING) continue;
            String v = c.getStringCellValue().trim();
            if (v.equals("班别")) banBieCol = i;
            else if (v.equals("班制")) banZhiCol = i;
        }
        if (banBieCol < 0 || banZhiCol <= banBieCol) return;
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, banBieCol, banZhiCol - 1));
    }

    private void setDateLeftAlign(Workbook wb, Sheet sheet) {
        Row row1 = sheet.getRow(1);
        if (row1 == null) return;
        Cell cell = row1.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return;
        CellStyle style = wb.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        cell.setCellStyle(style);
    }

    private void addBorders(Workbook wb, Sheet sheet) {
        CellStyle borderStyle = wb.createCellStyle();
        borderStyle.setBorderTop(BorderStyle.THIN);
        borderStyle.setBorderBottom(BorderStyle.THIN);
        borderStyle.setBorderLeft(BorderStyle.THIN);
        borderStyle.setBorderRight(BorderStyle.THIN);
        borderStyle.setAlignment(HorizontalAlignment.CENTER);
        borderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        borderStyle.setFillPattern(FillPatternType.NO_FILL);
        CellStyle noBorderStyle = wb.createCellStyle();
        noBorderStyle.setBorderTop(BorderStyle.NONE);
        noBorderStyle.setBorderBottom(BorderStyle.NONE);
        noBorderStyle.setBorderLeft(BorderStyle.NONE);
        noBorderStyle.setBorderRight(BorderStyle.NONE);
        noBorderStyle.setFillPattern(FillPatternType.NO_FILL);
        // 找到"备注"行
        int lastDataRow = sheet.getLastRowNum();
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell first = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (first != null && first.getCellType() == CellType.STRING
                    && first.getStringCellValue().trim().contains("备注")) {
                lastDataRow = r;
                break;
            }
        }
        // 找到表格实际最大列号（遍历前5行取最大 lastCellNum）
        int tableLastCol = 0;
        for (int r = 0; r < 5 && r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && row.getLastCellNum() - 1 > tableLastCol) {
                tableLastCol = row.getLastCellNum() - 1;
            }
        }
        // 收集所有合并区域的内部单元格（非左上角）
        java.util.Set<String> interiorCells = new java.util.HashSet<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            org.apache.poi.ss.util.CellRangeAddress region = sheet.getMergedRegion(i);
            for (int mr = region.getFirstRow(); mr <= region.getLastRow(); mr++) {
                for (int mc = region.getFirstColumn(); mc <= region.getLastColumn(); mc++) {
                    if (mr != region.getFirstRow() || mc != region.getFirstColumn()) {
                        interiorCells.add(mr + ":" + mc);
                    }
                }
            }
        }
        // 表格范围内加边框，范围外清除内容和边框变成空白
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                if (interiorCells.contains(r + ":" + c)) continue;
                Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                if (r <= lastDataRow && c <= tableLastCol) {
                    cell.setCellStyle(borderStyle);
                } else {
                    // 表格范围外：清空内容，去掉边框
                    cell.setCellValue((String) null);
                    cell.setCellStyle(noBorderStyle);
                }
            }
        }
    }

    private void copySheetContent(Sheet src, Sheet dest) {
        org.apache.poi.ss.usermodel.Workbook srcWb = src.getWorkbook();
        org.apache.poi.ss.usermodel.Workbook destWb = dest.getWorkbook();
        Map<Integer, CellStyle> styleCache = new HashMap<>();
        List<int[]> mergedRegions = new ArrayList<>();
        for (int i = 0; i < src.getNumMergedRegions(); i++) {
            try {
                org.apache.poi.ss.util.CellRangeAddress region = src.getMergedRegion(i);
                mergedRegions.add(new int[]{region.getFirstRow(), region.getLastRow(), region.getFirstColumn(), region.getLastColumn()});
            } catch (Exception ignored) {}
        }
        // 计算表格实际最大列号（取前几行的最大 lastCellNum）
        int tableLastCol = 0;
        for (int r = 0; r < 6 && r <= src.getLastRowNum(); r++) {
            Row row = src.getRow(r);
            if (row != null && row.getLastCellNum() - 1 > tableLastCol) tableLastCol = row.getLastCellNum() - 1;
        }
        for (int r = 0; r <= src.getLastRowNum(); r++) {
            Row srcRow = src.getRow(r);
            if (srcRow == null) continue;
            Row destRow = dest.createRow(r);
            destRow.setHeight(srcRow.getHeight());
            int lastCol = Math.max(srcRow.getLastCellNum() - 1, tableLastCol);
            for (int c = 0; c <= lastCol; c++) {
                Cell srcCell = srcRow.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                Cell destCell = destRow.createCell(c);
                CellType ct = srcCell.getCellType();
                if (ct == CellType.STRING) destCell.setCellValue(srcCell.getStringCellValue());
                else if (ct == CellType.NUMERIC) destCell.setCellValue(srcCell.getNumericCellValue());
                else if (ct == CellType.BOOLEAN) destCell.setCellValue(srcCell.getBooleanCellValue());
                else if (ct == CellType.FORMULA) destCell.setCellValue(srcCell.getCellFormula());
                try {
                    int srcStyleIdx = srcCell.getCellStyle().getIndex();
                    CellStyle destStyle = styleCache.get(srcStyleIdx);
                    if (destStyle == null) { destStyle = copyCellStyle(destWb, srcWb, srcCell.getCellStyle()); styleCache.put(srcStyleIdx, destStyle); }
                    destCell.setCellStyle(destStyle);
                } catch (Exception ignored) {}
            }
        }
        for (int i = 0; i <= tableLastCol; i++) { try { dest.setColumnWidth(i, src.getColumnWidth(i)); } catch (Exception ignored) {} }
        for (int[] region : mergedRegions) { try { dest.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(region[0], region[1], region[2], region[3])); } catch (Exception ignored) {} }
    }

    private CellStyle copyCellStyle(org.apache.poi.ss.usermodel.Workbook destWb, org.apache.poi.ss.usermodel.Workbook srcWb, CellStyle srcStyle) {
        CellStyle destStyle = destWb.createCellStyle();
        // 从源workbook获取字体，复制完整属性
        try {
            org.apache.poi.ss.usermodel.Font srcFont = srcWb.getFontAt(srcStyle.getFontIndex());
            org.apache.poi.ss.usermodel.Font destFont = destWb.createFont();
            destFont.setFontName(srcFont.getFontName());
            destFont.setFontHeightInPoints(srcFont.getFontHeightInPoints());
            destFont.setBold(srcFont.getBold());
            destFont.setItalic(srcFont.getItalic());
            destFont.setStrikeout(srcFont.getStrikeout());
            destFont.setUnderline(srcFont.getUnderline());
            try { destFont.setColor(srcFont.getColor()); } catch (Exception ignored) {}
            destStyle.setFont(destFont);
        } catch (Exception ignored) {}
        destStyle.setBorderTop(srcStyle.getBorderTop());
        destStyle.setBorderBottom(srcStyle.getBorderBottom());
        destStyle.setBorderLeft(srcStyle.getBorderLeft());
        destStyle.setBorderRight(srcStyle.getBorderRight());
        destStyle.setAlignment(srcStyle.getAlignment());
        destStyle.setVerticalAlignment(srcStyle.getVerticalAlignment());
        destStyle.setFillPattern(srcStyle.getFillPattern());
        try { destStyle.setFillForegroundColor(srcStyle.getFillForegroundColor()); } catch (Exception ignored) {}
        try { destStyle.setWrapText(srcStyle.getWrapText()); } catch (Exception ignored) {}
        return destStyle;
    }

    private void fillSheetFromTemplate(Workbook wb, Sheet sheet, StaffLedger ledger) {
        CellStyle dataStyle = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font dataFont = wb.createFont();
        dataFont.setColor(IndexedColors.BLACK.getIndex());
        dataStyle.setFont(dataFont);
        dataStyle.setAlignment(HorizontalAlignment.CENTER);
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        List<StaffLedgerDetail> details = staffLedgerDetailMapper.findByLedgerId(ledger.getId());
        Map<Long, EmployeeBasic> empMap = details.stream()
                .map(StaffLedgerDetail::getEmployeeBasicId).distinct()
                .map(id -> employeeBasicMapper.findById(id))
                .filter(e -> e != null)
                .collect(Collectors.toMap(EmployeeBasic::getId, e -> e));

        Row row4 = sheet.getRow(3);
        Row subRow = sheet.getRow(4);
        Map<String, int[]> shiftColMap = new HashMap<>();
        Map<String, int[]> extraShiftColsMap = new LinkedHashMap<>();
        if (row4 != null) {
            List<int[]> shiftPositions = new ArrayList<>();
            List<String> shiftNames = new ArrayList<>();
            for (int i = 3; i <= 25; i++) {
                Cell c = row4.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (c == null || c.getCellType() != CellType.STRING || c.getStringCellValue().isBlank()) continue;
                shiftPositions.add(new int[]{i});
                shiftNames.add(c.getStringCellValue().trim());
            }
            for (int idx = 0; idx < shiftNames.size(); idx++) {
                String name = shiftNames.get(idx);
                int colStart = shiftPositions.get(idx)[0];
                int colEnd = (idx + 1 < shiftPositions.size()) ? shiftPositions.get(idx + 1)[0] - 1 : 25;
                int span = 1;
                if (subRow != null) {
                    // 先检查合并单元格
                    for (int mr = 0; mr < sheet.getNumMergedRegions(); mr++) {
                        org.apache.poi.ss.util.CellRangeAddress region = sheet.getMergedRegion(mr);
                        if (region.getFirstRow() == subRow.getRowNum()
                                && region.getFirstColumn() >= colStart
                                && region.getFirstColumn() <= colEnd) {
                            int mergedSpan = region.getLastColumn() - region.getFirstColumn() + 1;
                            if (mergedSpan > span) span = mergedSpan;
                        }
                    }
                    // 再数非空单元格（兼容无合并单元格的模板）
                    int count = 0;
                    for (int c = colStart; c <= colEnd; c++) {
                        Cell sc = subRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        if (sc != null && sc.getCellType() == CellType.STRING && "姓名".equals(sc.getStringCellValue().trim())) count++;
                    }
                    if (count > span) span = count;
                }
                boolean matched = false;
                for (Map.Entry<String, String> e : STANDARD_SHIFT_MAP.entrySet()) {
                    if (name.contains(e.getKey()) && !(e.getKey().equals("半班") && shiftNames.contains("预备"))) {
                        shiftColMap.put(e.getValue(), new int[]{colStart, colStart + span - 1}); matched = true; break;
                    }
                }
                if (!matched && name.equals("日勤")) { shiftColMap.put("dailyName", new int[]{colStart, colStart}); matched = true; }
                if (!matched) extraShiftColsMap.put(name, new int[]{colStart, colStart + span - 1});
            }
        }

        Row row3 = sheet.getRow(2);
        int shiftCatCol = -1, dailyNameCol = -1, identityCol = -1;
        if (row3 != null) {
            for (int i = 3; i <= 25; i++) {
                Cell c = row3.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (c == null || c.getCellType() != CellType.STRING) continue;
                String v = c.getStringCellValue().trim();
                if (v.equals("班制")) shiftCatCol = i;
                else if (v.equals("日勤")) dailyNameCol = i;
                else if (v.equals("职务")) identityCol = i;
            }
        }

        for (int di = 0; di < details.size(); di++) {
            Row row = sheet.getRow(5 + di);
            if (row == null) row = sheet.createRow(5 + di);
            StaffLedgerDetail d = details.get(di);
            EmployeeBasic emp = empMap.get(d.getEmployeeBasicId());
            setCell(row, 0, d.getStationPoint(), dataStyle);
            setCell(row, 1, d.getTeamName() != null ? d.getTeamName() : (emp != null ? emp.getTeamName() : ""), dataStyle);
            setCell(row, 2, d.getWorkType() != null ? d.getWorkType() : (emp != null ? emp.getWorkType() : ""), dataStyle);

            Map<String, String[]> dataMap = new HashMap<>();
            dataMap.put("jiaBan", new String[]{d.getJiaBan1(), d.getJiaBan2()});
            dataMap.put("yiBan", new String[]{d.getYiBan1(), d.getYiBan2()});
            dataMap.put("bingBan", new String[]{d.getBingBan1(), d.getBingBan2()});
            dataMap.put("dingBan", new String[]{d.getDingBan1(), d.getDingBan2()});
            dataMap.put("yuBei", new String[]{d.getYuBei1(), d.getYuBei2(), d.getYuBei3(), d.getYuBei4()});
            dataMap.put("dailyName", new String[]{d.getDailyName(), null});
            for (Map.Entry<String, int[]> e : shiftColMap.entrySet()) {
                String[] vals = dataMap.get(e.getKey());
                if (vals == null) continue;
                int[] cols = e.getValue();
                setCell(row, cols[0], vals[0], dataStyle);
                for (int ci = 1; ci < cols.length && ci < vals.length; ci++) {
                    setCell(row, cols[0] + ci, vals[ci], dataStyle);
                }
            }
            Map<String, String[]> jsonData = parseExtraShiftJson(d.getExtraShiftJson());
            for (Map.Entry<String, int[]> e : extraShiftColsMap.entrySet()) {
                String shiftName = e.getKey();
                int[] cols = e.getValue();
                // 先按原始名查，再按"extra:"前缀查（兼容前端带前缀存储的情况）
                String[] vals = jsonData.get(shiftName);
                if (vals == null) vals = jsonData.get("extra:" + shiftName);
                setCell(row, cols[0], vals != null ? vals[0] : null, dataStyle);
                if (cols[1] != cols[0]) setCell(row, cols[1], vals != null ? vals[1] : null, dataStyle);
            }
            if (shiftCatCol >= 0) setCell(row, shiftCatCol, d.getShiftCategory(), dataStyle);
            if (dailyNameCol >= 0 && !shiftColMap.containsKey("dailyName")) setCell(row, dailyNameCol, d.getDailyName(), dataStyle);
            if (identityCol >= 0) setCell(row, identityCol, d.getIdentityType(), dataStyle);
        }

        // 填充编外人员、在岗人数和备注
        Integer inWorkCount = ledger.getInWorkCount();
        String remark = ledger.getRemark();
        Long nonWorkingCount = employeeBasicMapper.countNonWorkingDistributedByOrgUnitId(ledger.getOrgUnitId());
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell first = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (first == null || first.getCellType() != CellType.STRING) continue;
            String label = first.getStringCellValue().trim();
            Cell valCell = row.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            valCell.setCellStyle(dataStyle);
            if (label.contains("编外人员")) valCell.setCellValue(nonWorkingCount != null ? nonWorkingCount : 0);
            else if (label.contains("在岗人数")) valCell.setCellValue(inWorkCount != null ? inWorkCount : 0);
            else if (label.contains("备注")) valCell.setCellValue(remark != null ? remark : "");
        }
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    public byte[] exportEmployeeBasicToExcel(Long orgUnitId, String categoryMajor, Integer retirementAge) throws IOException {
        List<EmployeeBasic> employees;
        String sheetName;
        if (orgUnitId != null) {
            employees = employeeBasicMapper.findFiltered(orgUnitId, categoryMajor, retirementAge);
            OrgUnit org = orgUnitMapper.findById(orgUnitId);
            sheetName = org != null ? org.getOrgName() + "现员基础表" : "现员基础表";
        } else {
            employees = employeeBasicMapper.findFiltered(null, categoryMajor, retirementAge);
            sheetName = "全部现员基础表";
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true); headerFont.setFontHeightInPoints((short) 11);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN); headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN); headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER); headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN); dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN); dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setAlignment(HorizontalAlignment.CENTER); dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            String[] headers = {"身份证号", "姓名", "性别", "出生日期", "工种", "实际工种", "身份", "人员类别大类", "人员类别小类", "年龄", "劳动班制", "班组长", "科室车间", "部门班组"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) { headerRow.createCell(i).setCellValue(headers[i]); headerRow.getCell(i).setCellStyle(headerStyle); }

            int rowNum = 1;
            for (EmployeeBasic emp : employees) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(emp.getIdCardNo()); row.createCell(1).setCellValue(emp.getEmpName());
                row.createCell(2).setCellValue(emp.getGender());
                row.createCell(3).setCellValue(emp.getBirthDate() != null ? emp.getBirthDate() : "");
                row.createCell(4).setCellValue(emp.getWorkType()); row.createCell(5).setCellValue(emp.getActualWorkType());
                row.createCell(6).setCellValue(emp.getIdentityType());
                row.createCell(7).setCellValue(emp.getCategoryMajor()); row.createCell(8).setCellValue(emp.getCategoryMinor());
                row.createCell(9).setCellValue(emp.getAge() != null ? emp.getAge() : 0);
                row.createCell(10).setCellValue(emp.getLaborShift());
                row.createCell(11).setCellValue(emp.getIsTeamLeader() != null ? emp.getIsTeamLeader() : "否");
                OrgUnit org = orgUnitMapper.findById(emp.getOrgUnitId());
                row.createCell(12).setCellValue(org != null ? org.getOrgName() : "");
                row.createCell(13).setCellValue(emp.getTeamName());
                for (int i = 0; i <= 13; i++) row.getCell(i).setCellStyle(dataStyle);
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 5000) sheet.setColumnWidth(i, 5000);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportLedgerToExcel(Long ledgerId) throws IOException {
        return fillTemplateExcel(ledgerId);
    }

    public byte[] exportLedgerDistributionExcel(Long ledgerId) throws IOException {
        return fillTemplateExcel(ledgerId);
    }

    public byte[] batchExportLedgerToExcel(List<Long> orgUnitIds, String month) throws IOException {
        if (orgUnitIds == null || orgUnitIds.isEmpty()) throw new BizException("请选择至少一个部门");
        String effectiveMonth = (month != null && !month.isBlank()) ? month : java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        // 单个部门：直接在模板上填数据，和单个导出完全一致
        if (orgUnitIds.size() == 1) {
            Long orgUnitId = orgUnitIds.get(0);
            StaffLedger ledger = staffLedgerMapper.findByOrgUnitAndMonth(orgUnitId, effectiveMonth);
            if (ledger == null) throw new BizException("该部门本月无台账数据");
            return fillTemplateExcel(ledger.getId());
        }
        // 多个部门：以第一个填充好的模板作为输出workbook，后续模板作为新sheet追加
        XSSFWorkbook workbook = null;
        try {
            boolean firstSheet = true;
            for (Long orgUnitId : orgUnitIds) {
                StaffLedger ledger = staffLedgerMapper.findByOrgUnitAndMonth(orgUnitId, effectiveMonth);
                if (ledger == null) continue;
                OrgUnit orgUnit = orgUnitMapper.findById(orgUnitId);
                File tplFile;
                try { tplFile = findTemplateFile(orgUnit != null ? orgUnit.getOrgName() : ""); } catch (Exception e) { continue; }
                String sheetName = orgUnit != null ? orgUnit.getOrgName() : String.valueOf(orgUnitId);
                if (sheetName.length() > 31) sheetName = sheetName.substring(0, 31);
                if (firstSheet) {
                    // 第一个部门：直接打开模板填数据，这个workbook就是最终输出
                    workbook = new XSSFWorkbook(new FileInputStream(tplFile));
                    Sheet sheet = workbook.getSheetAt(0);
                    fillSheetFromTemplate(workbook, sheet, ledger);
                    fillDateRow(workbook, sheet, ledger);
                    mergeBanBieRow(sheet);
                    addBorders(workbook, sheet);
                    setDateLeftAlign(workbook, sheet);
                    workbook.setSheetName(0, sheetName);
                    firstSheet = false;
                } else {
                    // 后续部门：打开各自模板填数据，然后复制sheet到输出workbook
                    try (Workbook tplWb = new XSSFWorkbook(new FileInputStream(tplFile))) {
                        Sheet tplSheet = tplWb.getSheetAt(0);
                        fillSheetFromTemplate(tplWb, tplSheet, ledger);
                        fillDateRow(tplWb, tplSheet, ledger);
                        mergeBanBieRow(tplSheet);
                        addBorders(tplWb, tplSheet);
                        setDateLeftAlign(tplWb, tplSheet);
                        Sheet outSheet = workbook.createSheet(sheetName);
                        copySheetContent(tplSheet, outSheet);
                    }
                }
            }
            if (workbook == null) throw new BizException("所选部门本月均无台账数据");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } finally { if (workbook != null) workbook.close(); }
    }

    public byte[] batchExportEmployeeBasicToExcel(List<Long> orgUnitIds) throws IOException {
        if (orgUnitIds == null || orgUnitIds.isEmpty()) throw new BizException("请选择至少一个部门");
        try (Workbook workbook = new XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true); headerFont.setFontHeightInPoints((short) 11);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN); headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN); headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER); headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN); dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN); dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setAlignment(HorizontalAlignment.CENTER); dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            String[] headers = {"身份证号", "姓名", "性别", "出生日期", "工种", "实际工种", "身份", "人员类别大类", "人员类别小类", "年龄", "劳动班制", "班组长", "科室车间", "部门班组"};
            for (Long orgUnitId : orgUnitIds) {
                OrgUnit org = orgUnitMapper.findById(orgUnitId);
                if (org == null) continue;
                List<EmployeeBasic> employees = employeeBasicMapper.findFiltered(orgUnitId, null, null);
                String sheetName = org.getOrgName();
                if (sheetName.length() > 31) sheetName = sheetName.substring(0, 31);
                Sheet sheet = workbook.createSheet(sheetName);
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) { headerRow.createCell(i).setCellValue(headers[i]); headerRow.getCell(i).setCellStyle(headerStyle); }
                int rowNum = 1;
                for (EmployeeBasic emp : employees) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(emp.getIdCardNo()); row.createCell(1).setCellValue(emp.getEmpName());
                    row.createCell(2).setCellValue(emp.getGender());
                    row.createCell(3).setCellValue(emp.getBirthDate() != null ? emp.getBirthDate() : "");
                    row.createCell(4).setCellValue(emp.getWorkType()); row.createCell(5).setCellValue(emp.getActualWorkType());
                    row.createCell(6).setCellValue(emp.getIdentityType());
                    row.createCell(7).setCellValue(emp.getCategoryMajor()); row.createCell(8).setCellValue(emp.getCategoryMinor());
                    row.createCell(9).setCellValue(emp.getAge() != null ? emp.getAge() : 0);
                    row.createCell(10).setCellValue(emp.getLaborShift());
                    row.createCell(11).setCellValue(emp.getIsTeamLeader() != null ? emp.getIsTeamLeader() : "否");
                    row.createCell(12).setCellValue(org.getOrgName());
                    row.createCell(13).setCellValue(emp.getTeamName());
                    for (int i = 0; i <= 13; i++) row.getCell(i).setCellStyle(dataStyle);
                }
                for (int i = 0; i < headers.length; i++) { sheet.autoSizeColumn(i); if (sheet.getColumnWidth(i) < 5000) sheet.setColumnWidth(i, 5000); }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(); workbook.write(out); return out.toByteArray();
        }
    }

    public TemplateFieldsResponse getTemplateFields(Long orgUnitId) {
        OrgUnit orgUnit = orgUnitMapper.findById(orgUnitId);
        if (orgUnit == null) throw new BizException("组织单位不存在");
        File tplFile = findTemplateFile(orgUnit.getOrgName());
        try (Workbook wb = new XSSFWorkbook(new FileInputStream(tplFile))) {
            Sheet sheet = wb.getSheetAt(0);
            String title = "";
            Row row1 = sheet.getRow(0);
            if (row1 != null) {
                Cell c = row1.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (c != null && c.getCellType() == CellType.STRING) title = c.getStringCellValue().trim();
            }

            // 计算模板数据区域实际行数（第6行到"编外人员"行的上一行）
            int dataStartRow = 5;
            int lastDataRow = sheet.getLastRowNum();
            for (int r = dataStartRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Cell first = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (first != null && first.getCellType() == CellType.STRING
                        && first.getStringCellValue().trim().contains("编外人员")) {
                    lastDataRow = r - 1;
                    break;
                }
            }
            int templateRowCount = Math.max(0, lastDataRow - dataStartRow + 1);

            List<FieldItem> fields = new ArrayList<>();
            fields.add(new FieldItem("stationPoint", "岗点", false));
            fields.add(new FieldItem("teamName", "班组", false));
            fields.add(new FieldItem("workType", "岗位", false));
            Row row4 = sheet.getRow(3);
            String[][] standardMapping = {
                {"甲班", "jiaBan"}, {"乙班", "yiBan"}, {"丙班", "bingBan"}, {"丁班", "dingBan"},
                {"预备", "yuBei"}, {"半班", "yuBei"},
            };
            boolean[] standardUsed = new boolean[5];
            // 先收集所有班次名称，用于判断"半班"与"预备"并存的情况
            List<String> allShiftNames = new ArrayList<>();
            for (int i = 3; i <= 25; i++) {
                Cell c = row4.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (c != null && c.getCellType() == CellType.STRING && !c.getStringCellValue().isBlank()) allShiftNames.add(c.getStringCellValue().trim());
            }
            boolean hasYuBei = allShiftNames.stream().anyMatch(n -> n.contains("预备"));
            for (int i = 3; i <= 25; i++) {
                Cell c = row4.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (c == null || c.getCellType() != CellType.STRING || c.getStringCellValue().isBlank()) continue;
                String name = c.getStringCellValue().trim();
                boolean matched = false;
                for (String[] mapping : standardMapping) {
                    if (name.contains(mapping[0]) && !(mapping[0].equals("半班") && hasYuBei)) {
                        int idx = switch (mapping[1]) {
                            case "jiaBan" -> 0; case "yiBan" -> 1; case "bingBan" -> 2; case "dingBan" -> 3; case "yuBei" -> 4; default -> -1;
                        };
                        if (idx >= 0 && !standardUsed[idx]) {
                            standardUsed[idx] = true;
                            int cols = 2;
                            if (mapping[1].equals("yuBei")) {
                                Row subRow = sheet.getRow(4);
                                int colEnd2 = 25;
                                for (int j = i + 1; j <= 25; j++) {
                                    Cell nc = row4.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                                    if (nc != null && nc.getCellType() == CellType.STRING && !nc.getStringCellValue().isBlank()) { colEnd2 = j - 1; break; }
                                }
                                if (subRow != null) {
                                    // 检查合并单元格
                                    for (int mr = 0; mr < sheet.getNumMergedRegions(); mr++) {
                                        org.apache.poi.ss.util.CellRangeAddress region = sheet.getMergedRegion(mr);
                                        if (region.getFirstRow() == subRow.getRowNum()
                                                && region.getFirstColumn() >= i
                                                && region.getFirstColumn() <= colEnd2) {
                                            int mergedSpan = region.getLastColumn() - region.getFirstColumn() + 1;
                                            if (mergedSpan > cols) cols = mergedSpan;
                                        }
                                    }
                                    // 再数非空单元格
                                    int cnt = 0;
                                    for (int c2 = i; c2 <= colEnd2; c2++) {
                                        Cell sc = subRow.getCell(c2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                                        if (sc != null && sc.getCellType() == CellType.STRING && "姓名".equals(sc.getStringCellValue().trim())) cnt++;
                                    }
                                    if (cnt > cols) cols = cnt;
                                }
                            }
                            for (int n = 1; n <= cols; n++) {
                                fields.add(new FieldItem(mapping[1] + n, name, true));
                            }
                            matched = true;
                            break;
                        }
                    }
                }
                if (!matched) fields.add(new FieldItem("extra:" + name, name, true));
            }
            fields.add(new FieldItem("shiftCategory", "班制", false));
            fields.add(new FieldItem("dailyName", "日勤", false));
            fields.add(new FieldItem("identityType", "职务", false));
            return new TemplateFieldsResponse(title, templateRowCount, fields);
        } catch (IOException e) { throw new BizException("读取模板文件失败: " + e.getMessage()); }
    }

    public byte[] exportLedgerTemplatePdf(Long ledgerId) throws IOException {
        StaffLedger ledger = staffLedgerMapper.findById(ledgerId);
        if (ledger == null) throw new BizException("台账不存在");
        OrgUnit orgUnit = orgUnitMapper.findById(ledger.getOrgUnitId());
        List<StaffLedgerDetail> details = staffLedgerDetailMapper.findByLedgerId(ledgerId);
        Map<Long, EmployeeBasic> empMap = details.stream()
                .map(StaffLedgerDetail::getEmployeeBasicId).distinct()
                .map(id -> employeeBasicMapper.findById(id)).filter(e -> e != null)
                .collect(Collectors.toMap(EmployeeBasic::getId, e -> e));
        List<String> extraShiftNames = new ArrayList<>();
        try {
            File tplFile = findTemplateFile(orgUnit != null ? orgUnit.getOrgName() : "");
            try (Workbook twb = new XSSFWorkbook(new FileInputStream(tplFile))) {
                Row r4 = twb.getSheetAt(0).getRow(3);
                boolean hasYuBei = false;
                for (int i = 3; i <= 25; i++) {
                    Cell c = r4.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (c != null && c.getCellType() == CellType.STRING && c.getStringCellValue().trim().contains("预备")) { hasYuBei = true; break; }
                }
                for (int i = 3; i <= 25; i++) {
                    Cell c = r4.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (c == null || c.getCellType() != CellType.STRING || c.getStringCellValue().isBlank()) continue;
                    String name = c.getStringCellValue().trim();
                    boolean standard = false;
                    for (Map.Entry<String, String> e : STANDARD_SHIFT_MAP.entrySet()) {
                        if (name.contains(e.getKey()) && !(e.getKey().equals("半班") && hasYuBei)) { standard = true; break; }
                    }
                    if (!standard && !name.equals("日勤")) extraShiftNames.add(name);
                }
            }
        } catch (Exception ignored) {}
        List<String> headerNames = new ArrayList<>(List.of("岗点", "班组", "岗位"));
        List<Integer> headerColspans = new ArrayList<>(List.of(1, 1, 1));
        int yuBeiColspan = 2;
        for (String sn : List.of("甲班", "乙班", "丙班", "丁班", "预备")) {
            int cs = 2;
            if (sn.equals("预备")) {
                // 从模板检测预备实际列数
                try {
                    File tplFile2 = findTemplateFile(orgUnit != null ? orgUnit.getOrgName() : "");
                    try (Workbook twb2 = new XSSFWorkbook(new FileInputStream(tplFile2))) {
                        Row r4 = twb2.getSheetAt(0).getRow(3);
                        Row sr2 = twb2.getSheetAt(0).getRow(4);
                        for (int i = 3; i <= 25; i++) {
                            Cell c = r4.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                            if (c == null || c.getCellType() != CellType.STRING || !c.getStringCellValue().trim().contains("预备")) continue;
                            int colStart = i;
                            int colEnd = 25;
                            for (int j = i + 1; j <= 25; j++) {
                                Cell nc = r4.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                                if (nc != null && nc.getCellType() == CellType.STRING && !nc.getStringCellValue().isBlank()) { colEnd = j - 1; break; }
                            }
                            if (sr2 != null) {
                                // 检查合并单元格
                                Sheet tplSheet2 = twb2.getSheetAt(0);
                                for (int mr = 0; mr < tplSheet2.getNumMergedRegions(); mr++) {
                                    org.apache.poi.ss.util.CellRangeAddress region = tplSheet2.getMergedRegion(mr);
                                    if (region.getFirstRow() == sr2.getRowNum()
                                            && region.getFirstColumn() >= colStart
                                            && region.getFirstColumn() <= colEnd) {
                                        int mergedSpan = region.getLastColumn() - region.getFirstColumn() + 1;
                                        if (mergedSpan > cs) cs = mergedSpan;
                                    }
                                }
                                // 再数非空单元格
                                int cnt = 0;
                                for (int c2 = colStart; c2 <= colEnd; c2++) {
                                    Cell sc = sr2.getCell(c2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                                    if (sc != null && sc.getCellType() == CellType.STRING && "姓名".equals(sc.getStringCellValue().trim())) cnt++;
                                }
                                if (cnt > cs) cs = cnt;
                            }
                            break;
                        }
                    }
                } catch (Exception ignored) {}
                yuBeiColspan = cs;
            }
            headerNames.add(sn);
            headerColspans.add(cs);
        }
        for (String en : extraShiftNames) { headerNames.add(en); headerColspans.add(2); }
        headerNames.addAll(List.of("班制", "日勤", "职务"));
        headerColspans.addAll(List.of(1, 1, 1));
        int totalCols = headerColspans.stream().mapToInt(Integer::intValue).sum();
        int pairCols = 0;
        for (int i = 0; i < headerColspans.size(); i++) {
            if (!headerNames.get(i).equals("日勤")) pairCols += headerColspans.get(i);
        }
        int hasDaily = headerNames.contains("日勤") ? 1 : 0;
        int shiftPairCount = pairCols - hasDaily;
        float[] colWidths = new float[totalCols];
        colWidths[0] = 6; colWidths[1] = 8; colWidths[2] = 8;
        for (int i = 3; i < 3 + shiftPairCount * 2; i++) colWidths[i] = 4.5f;
        for (int i = 3 + shiftPairCount * 2; i < totalCols; i++) colWidths[i] = 5;
        com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(totalCols);
        table.setWidths(colWidths); table.setWidthPercentage(100);
        com.lowagie.text.pdf.PdfPCell titleCell = makePdfCell((orgUnit != null ? orgUnit.getOrgName() : "") + "  现员分布台账", 12, true, Element.ALIGN_CENTER);
        titleCell.setColspan(totalCols); titleCell.setPadding(8); table.addCell(titleCell);
        com.lowagie.text.pdf.PdfPCell dateCell = makePdfCell("日期：" + (ledger.getLedgerMonth() != null ? ledger.getLedgerMonth() : ""), 10, false, Element.ALIGN_LEFT);
        dateCell.setColspan(totalCols); table.addCell(dateCell);
        for (int i = 0; i < headerNames.size(); i++) {
            com.lowagie.text.pdf.PdfPCell cell = makePdfCell(headerNames.get(i), 9, true, Element.ALIGN_CENTER);
            if (headerColspans.get(i) > 1) cell.setColspan(headerColspans.get(i));
            table.addCell(cell);
        }
        for (int i = 0; i < 3; i++) table.addCell(makePdfCell("", 9, true, Element.ALIGN_CENTER));
        for (int i = 3; i < headerNames.size(); i++) {
            int cs = headerColspans.get(i);
            if (headerNames.get(i).equals("日勤")) {
                table.addCell(makePdfCell("", 9, true, Element.ALIGN_CENTER));
            } else {
                for (int j = 0; j < cs; j++) table.addCell(makePdfCell("姓名", 9, true, Element.ALIGN_CENTER));
            }
        }
        table.addCell(makePdfCell("", 9, true, Element.ALIGN_CENTER));
        table.addCell(makePdfCell("姓名", 9, true, Element.ALIGN_CENTER));
        table.addCell(makePdfCell("", 9, true, Element.ALIGN_CENTER));
        for (StaffLedgerDetail d : details) {
            EmployeeBasic emp = empMap.get(d.getEmployeeBasicId());
            table.addCell(makePdfCell(pdfStr(d.getStationPoint()), 8, false, Element.ALIGN_CENTER));
            table.addCell(makePdfCell(pdfStr(d.getTeamName(), emp != null ? emp.getTeamName() : ""), 8, false, Element.ALIGN_CENTER));
            table.addCell(makePdfCell(pdfStr(d.getWorkType(), emp != null ? emp.getWorkType() : ""), 8, false, Element.ALIGN_CENTER));
            table.addCell(makePdfCell(pdfStr(d.getJiaBan1()), 8, false, Element.ALIGN_CENTER));
            table.addCell(makePdfCell(pdfStr(d.getJiaBan2()), 8, false, Element.ALIGN_CENTER));
            table.addCell(makePdfCell(pdfStr(d.getYiBan1()), 8, false, Element.ALIGN_CENTER));
            table.addCell(makePdfCell(pdfStr(d.getYiBan2()), 8, false, Element.ALIGN_CENTER));
            table.addCell(makePdfCell(pdfStr(d.getBingBan1()), 8, false, Element.ALIGN_CENTER));
            table.addCell(makePdfCell(pdfStr(d.getBingBan2()), 8, false, Element.ALIGN_CENTER));
            table.addCell(makePdfCell(pdfStr(d.getDingBan1()), 8, false, Element.ALIGN_CENTER));
            table.addCell(makePdfCell(pdfStr(d.getDingBan2()), 8, false, Element.ALIGN_CENTER));
            String[] yuBeiVals = {d.getYuBei1(), d.getYuBei2(), d.getYuBei3(), d.getYuBei4()};
            for (int yb = 0; yb < yuBeiColspan && yb < yuBeiVals.length; yb++) {
                table.addCell(makePdfCell(pdfStr(yuBeiVals[yb]), 8, false, Element.ALIGN_CENTER));
            }
            Map<String, String[]> jsonData = parseExtraShiftJson(d.getExtraShiftJson());
            for (String shiftName : extraShiftNames) {
                String[] vals = jsonData.get(shiftName);
                table.addCell(makePdfCell(pdfStr(vals != null ? vals[0] : ""), 8, false, Element.ALIGN_CENTER));
                table.addCell(makePdfCell(pdfStr(vals != null ? vals[1] : ""), 8, false, Element.ALIGN_CENTER));
            }
            table.addCell(makePdfCell(pdfStr(d.getShiftCategory()), 8, false, Element.ALIGN_CENTER));
            table.addCell(makePdfCell(pdfStr(d.getDailyName()), 8, false, Element.ALIGN_CENTER));
            table.addCell(makePdfCell(pdfStr(d.getIdentityType(), emp != null ? emp.getIdentityType() : ""), 8, false, Element.ALIGN_CENTER));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 15, 15, 15, 15);
        com.lowagie.text.pdf.PdfWriter.getInstance(doc, out); doc.open(); doc.add(table);
        Font countFont = new Font(Font.HELVETICA, 10);
        doc.add(new Paragraph("  编外人员：" + employeeBasicMapper.countNonWorkingDistributedByOrgUnitId(ledger.getOrgUnitId()), countFont));
        doc.add(new Paragraph("  在岗人数：" + (ledger.getInWorkCount() != null ? ledger.getInWorkCount() : ""), countFont));
        if (ledger.getRemark() != null && !ledger.getRemark().isBlank()) doc.add(new Paragraph("  备注：" + ledger.getRemark(), countFont));
        doc.close(); return out.toByteArray();
    }

    private com.lowagie.text.pdf.PdfPCell makePdfCell(String text, float fontSize, boolean bold, int align) {
        int style = bold ? Font.BOLD : Font.NORMAL;
        Font font = new Font(Font.HELVETICA, fontSize, style);
        com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell(new Paragraph(text != null ? text : "", font));
        cell.setHorizontalAlignment(align); cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderWidth(0.5f); cell.setPadding(3);
        return cell;
    }

    private String pdfStr(String val) { return val != null ? val : ""; }
    private String pdfStr(String val, String fallback) { return val != null ? val : (fallback != null ? fallback : ""); }

    public byte[] exportLedgerToPdf(Long ledgerId) throws IOException {
        StaffLedger ledger = staffLedgerMapper.findById(ledgerId);
        if (ledger == null) throw new BizException("台账不存在");
        OrgUnit orgUnit = orgUnitMapper.findById(ledger.getOrgUnitId());
        List<StaffLedgerDetail> details = staffLedgerDetailMapper.findByLedgerId(ledgerId);
        Map<Long, EmployeeBasic> empMap = details.stream().map(StaffLedgerDetail::getEmployeeBasicId).distinct()
                .map(id -> employeeBasicMapper.findById(id)).filter(e -> e != null).collect(Collectors.toMap(EmployeeBasic::getId, e -> e));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
        com.lowagie.text.pdf.PdfWriter.getInstance(document, out); document.open();

        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        Paragraph title = new Paragraph((orgUnit != null ? orgUnit.getOrgName() : "") + "现员分布台账", titleFont);
        title.setAlignment(Element.ALIGN_CENTER); document.add(title);
        Font monthFont = new Font(Font.HELVETICA, 12);
        Paragraph month = new Paragraph(ledger.getLedgerMonth(), monthFont);
        month.setAlignment(Element.ALIGN_CENTER); document.add(month);

        Table table = new Table(9); table.setWidth(100); table.setSpacing(5);
        table.setHorizontalAlignment(com.lowagie.text.alignment.HorizontalAlignment.CENTER);
        Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        String[] headers = {"岗点", "班组", "岗位", "姓名", "年龄", "班制", "班组长", "非在岗", "原因"};
        for (String h : headers) table.addCell(new com.lowagie.text.Cell(new Paragraph(h, headerFont)));

        Font dataFont = new Font(Font.HELVETICA, 9);
        for (StaffLedgerDetail detail : details) {
            EmployeeBasic emp = empMap.get(detail.getEmployeeBasicId());
            table.addCell(new Paragraph(detail.getStationPoint() != null ? detail.getStationPoint() : "", dataFont));
            table.addCell(new Paragraph(emp != null ? emp.getTeamName() : "", dataFont));
            table.addCell(new Paragraph(emp != null ? emp.getWorkType() : "", dataFont));
            table.addCell(new Paragraph(emp != null ? emp.getEmpName() : "", dataFont));
            table.addCell(new Paragraph(emp != null && emp.getAge() != null ? String.valueOf(emp.getAge()) : "", dataFont));
            table.addCell(new Paragraph(emp != null ? emp.getLaborShift() : "", dataFont));
            table.addCell(new Paragraph(emp != null && emp.getIsTeamLeader() != null ? emp.getIsTeamLeader() : "否", dataFont));
            table.addCell(new Paragraph(emp != null && emp.getIsActive() == 0 ? "是" : "否", dataFont));
            table.addCell(new Paragraph(emp != null && emp.getIsActive() == 0 ? emp.getCategoryMajor() : "", dataFont));
        }
        document.add(table);
        Font countFont = new Font(Font.HELVETICA, 11);
        document.add(new Paragraph("在岗人数: " + (ledger.getInWorkCount() != null ? ledger.getInWorkCount() : ""), countFont));
        if (ledger.getRemark() != null && !ledger.getRemark().isBlank()) document.add(new Paragraph("备注: " + ledger.getRemark(), countFont));
        document.close(); return out.toByteArray();
    }
}
