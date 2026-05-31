package com.attendance.ledger.service;

import com.attendance.admin.mapper.OrgUnitMapper;
import com.attendance.admin.model.OrgUnit;
import com.attendance.exception.BizException;
import com.attendance.ledger.mapper.EmployeeBasicMapper;
import com.attendance.ledger.mapper.StaffLedgerDetailMapper;
import com.attendance.ledger.mapper.StaffLedgerMapper;
import com.attendance.ledger.model.EmployeeBasic;
import com.attendance.ledger.model.StaffLedger;
import com.attendance.ledger.model.StaffLedgerDetail;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Table;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerExportService {

    private final EmployeeBasicMapper employeeBasicMapper;
    private final StaffLedgerMapper staffLedgerMapper;
    private final StaffLedgerDetailMapper staffLedgerDetailMapper;
    private final OrgUnitMapper orgUnitMapper;

    public byte[] exportEmployeeBasicToExcel(Long orgUnitId) throws IOException {
        List<EmployeeBasic> employees;
        String sheetName;
        if (orgUnitId != null) {
            employees = employeeBasicMapper.findDistributedByOrgUnitId(orgUnitId);
            OrgUnit org = orgUnitMapper.findById(orgUnitId);
            sheetName = org != null ? org.getOrgName() + "现员基础表" : "现员基础表";
        } else {
            employees = employeeBasicMapper.findAll(null, 1);
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

            String[] headers = {"身份证号", "姓名", "性别", "出生日期", "工种", "身份", "人员类别大类", "人员类别小类", "年龄", "劳动班制", "班组长", "科室车间", "部门班组"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) { headerRow.createCell(i).setCellValue(headers[i]); headerRow.getCell(i).setCellStyle(headerStyle); }

            int rowNum = 1;
            for (EmployeeBasic emp : employees) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(emp.getIdCardNo()); row.createCell(1).setCellValue(emp.getEmpName());
                row.createCell(2).setCellValue(emp.getGender());
                row.createCell(3).setCellValue(emp.getBirthDate() != null ? emp.getBirthDate() : "");
                row.createCell(4).setCellValue(emp.getWorkType()); row.createCell(5).setCellValue(emp.getIdentityType());
                row.createCell(6).setCellValue(emp.getCategoryMajor()); row.createCell(7).setCellValue(emp.getCategoryMinor());
                row.createCell(8).setCellValue(emp.getAge() != null ? emp.getAge() : 0);
                row.createCell(9).setCellValue(emp.getLaborShift());
                row.createCell(10).setCellValue(emp.getIsTeamLeader() != null ? emp.getIsTeamLeader() : "否");
                OrgUnit org = orgUnitMapper.findById(emp.getOrgUnitId());
                row.createCell(11).setCellValue(org != null ? org.getOrgName() : "");
                row.createCell(12).setCellValue(emp.getTeamName());
                for (int i = 0; i <= 12; i++) row.getCell(i).setCellStyle(dataStyle);
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
        StaffLedger ledger = staffLedgerMapper.findById(ledgerId);
        if (ledger == null) throw new BizException("台账不存在");
        OrgUnit orgUnit = orgUnitMapper.findById(ledger.getOrgUnitId());
        List<StaffLedgerDetail> details = staffLedgerDetailMapper.findByLedgerId(ledgerId);
        Map<Long, EmployeeBasic> empMap = details.stream().map(StaffLedgerDetail::getEmployeeBasicId).distinct()
                .map(id -> employeeBasicMapper.findById(id)).filter(e -> e != null).collect(Collectors.toMap(EmployeeBasic::getId, e -> e));

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("现员分布台账");
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true); headerFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = workbook.createCellStyle(); titleStyle.setFont(headerFont); titleStyle.setAlignment(HorizontalAlignment.CENTER);
            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue((orgUnit != null ? orgUnit.getOrgName() : "") + "现员分布台账");
            titleRow.getCell(0).setCellStyle(titleStyle);
            Row monthRow = sheet.createRow(1); monthRow.createCell(0).setCellValue(ledger.getLedgerMonth());

            String[] headers = {"岗点", "班组", "岗位", "姓名", "年龄", "班制", "是否班组长", "非在岗", "非在岗原因"};
            Row headerRow = sheet.createRow(3);
            org.apache.poi.ss.usermodel.Font colHeaderFont = workbook.createFont(); colHeaderFont.setBold(true);
            CellStyle colHeaderStyle = workbook.createCellStyle(); colHeaderStyle.setFont(colHeaderFont);
            colHeaderStyle.setBorderBottom(BorderStyle.THIN); colHeaderStyle.setBorderTop(BorderStyle.THIN);
            colHeaderStyle.setBorderLeft(BorderStyle.THIN); colHeaderStyle.setBorderRight(BorderStyle.THIN);
            colHeaderStyle.setAlignment(HorizontalAlignment.CENTER);
            for (int i = 0; i < headers.length; i++) { headerRow.createCell(i).setCellValue(headers[i]); headerRow.getCell(i).setCellStyle(colHeaderStyle); }

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN); dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN); dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            CellStyle leaderStyle = workbook.createCellStyle(); leaderStyle.cloneStyleFrom(dataStyle);
            leaderStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex()); leaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle learnerStyle = workbook.createCellStyle(); learnerStyle.cloneStyleFrom(dataStyle);
            learnerStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex()); learnerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rowNum = 4;
            for (StaffLedgerDetail detail : details) {
                Row row = sheet.createRow(rowNum++);
                EmployeeBasic emp = empMap.get(detail.getEmployeeBasicId());
                row.createCell(0).setCellValue(detail.getStationPoint() != null ? detail.getStationPoint() : "");
                row.createCell(1).setCellValue(emp != null ? emp.getTeamName() : "");
                row.createCell(2).setCellValue(emp != null ? emp.getWorkType() : "");
                row.createCell(3).setCellValue(emp != null ? emp.getEmpName() : "");
                row.createCell(4).setCellValue(emp != null && emp.getAge() != null ? emp.getAge() : 0);
                row.createCell(5).setCellValue(emp != null ? emp.getLaborShift() : "");
                row.createCell(6).setCellValue(emp != null && emp.getIsTeamLeader() != null ? emp.getIsTeamLeader() : "否");
                row.createCell(7).setCellValue(emp != null && emp.getIsActive() == 0 ? "是" : "否");
                row.createCell(8).setCellValue(emp != null && emp.getIsActive() == 0 ? emp.getCategoryMajor() : "");
                CellStyle style = dataStyle;
                if (emp != null && emp.getIsTeamLeader() != null && !emp.getIsTeamLeader().equals("否")) style = leaderStyle;
                else if (emp != null && emp.getCategoryMinor() != null && (emp.getCategoryMinor().contains("学习") || emp.getCategoryMinor().contains("新职"))) style = learnerStyle;
                for (int i = 0; i <= 8; i++) row.getCell(i).setCellStyle(style);
            }
            Row countRow = sheet.createRow(rowNum + 1);
            countRow.createCell(0).setCellValue("在岗人数: " + (ledger.getInWorkCount() != null ? ledger.getInWorkCount() : ""));
            Row remarkRow = sheet.createRow(rowNum + 2);
            remarkRow.createCell(0).setCellValue("备注: " + (ledger.getRemark() != null ? ledger.getRemark() : ""));
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 5000) sheet.setColumnWidth(i, 5000);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(); workbook.write(out); return out.toByteArray();
        }
    }

    public byte[] exportLedgerDistributionExcel(Long ledgerId) throws IOException {
        StaffLedger ledger = staffLedgerMapper.findById(ledgerId);
        if (ledger == null) throw new BizException("台账不存在");
        OrgUnit orgUnit = orgUnitMapper.findById(ledger.getOrgUnitId());
        List<StaffLedgerDetail> details = staffLedgerDetailMapper.findByLedgerId(ledgerId);
        Map<Long, EmployeeBasic> empMap = details.stream().map(StaffLedgerDetail::getEmployeeBasicId).distinct()
                .map(id -> employeeBasicMapper.findById(id)).filter(e -> e != null).collect(Collectors.toMap(EmployeeBasic::getId, e -> e));

        // 固定班别列顺序
        String[] categories = {"日勤", "甲班", "乙班", "丙班", "丁班", "预备"};
        Map<String, List<String>> categoryNames = new LinkedHashMap<>();
        for (String cat : categories) categoryNames.put(cat, new ArrayList<>());

        for (StaffLedgerDetail detail : details) {
            EmployeeBasic emp = empMap.get(detail.getEmployeeBasicId());
            if (emp == null) continue;
            String cat = detail.getShiftCategory();
            if (cat == null || cat.isBlank()) cat = "其他";
            categoryNames.computeIfAbsent(cat, k -> new ArrayList<>()).add(emp.getEmpName());
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("现员分布台账");
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true); headerFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = workbook.createCellStyle(); titleStyle.setFont(headerFont); titleStyle.setAlignment(HorizontalAlignment.CENTER);
            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue((orgUnit != null ? orgUnit.getOrgName() : "") + "现员分布台账");
            titleRow.getCell(0).setCellStyle(titleStyle);
            Row monthRow = sheet.createRow(1); monthRow.createCell(0).setCellValue(ledger.getLedgerMonth());

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font colFont = workbook.createFont(); colFont.setBold(true);
            headerStyle.setFont(colFont);
            headerStyle.setBorderBottom(BorderStyle.THIN); headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN); headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN); dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN); dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setAlignment(HorizontalAlignment.CENTER);

            // 表头行：班别 | 日勤 | 甲班 | 乙班 | 丙班 | 丁班 | 预备 | 其他
            Row headerRow = sheet.createRow(3);
            headerRow.createCell(0).setCellValue("班别");
            headerRow.getCell(0).setCellStyle(headerStyle);
            int colIdx = 1;
            List<String> allCategories = new ArrayList<>();
            for (String cat : categories) {
                headerRow.createCell(colIdx).setCellValue(cat);
                headerRow.getCell(colIdx).setCellStyle(headerStyle);
                allCategories.add(cat);
                colIdx++;
            }
            // 添加"其他"列（如果有数据）
            if (categoryNames.containsKey("其他") && !categoryNames.get("其他").isEmpty()) {
                headerRow.createCell(colIdx).setCellValue("其他");
                headerRow.getCell(colIdx).setCellStyle(headerStyle);
                allCategories.add("其他");
            }

            // 姓名行
            int maxRows = categoryNames.values().stream().mapToInt(List::size).max().orElse(0);
            for (int r = 0; r < maxRows; r++) {
                Row row = sheet.createRow(4 + r);
                row.createCell(0).setCellValue("姓名");
                row.getCell(0).setCellStyle(dataStyle);
                for (int c = 0; c < allCategories.size(); c++) {
                    List<String> names = categoryNames.getOrDefault(allCategories.get(c), List.of());
                    String val = r < names.size() ? names.get(r) : "";
                    row.createCell(c + 1).setCellValue(val);
                    row.getCell(c + 1).setCellStyle(dataStyle);
                }
            }

            Row countRow = sheet.createRow(4 + maxRows + 1);
            countRow.createCell(0).setCellValue("在岗人数: " + (ledger.getInWorkCount() != null ? ledger.getInWorkCount() : ""));
            Row remarkRow = sheet.createRow(4 + maxRows + 2);
            remarkRow.createCell(0).setCellValue("备注: " + (ledger.getRemark() != null ? ledger.getRemark() : ""));

            for (int i = 0; i <= allCategories.size(); i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 4000) sheet.setColumnWidth(i, 4000);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(); workbook.write(out); return out.toByteArray();
        }
    }

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
