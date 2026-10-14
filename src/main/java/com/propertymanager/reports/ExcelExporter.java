package com.propertymanager.reports;

import com.propertymanager.reports.ReportService.ReportData;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Writes a ReportData to a formatted .xlsx file.
 *
 * Output layout:
 *   Row 0: Report title (merged, bold, larger)
 *   Row 1: Subtitle
 *   Row 2: Generated on
 *   Row 3: (blank)
 *   Row 4: Column headers (bold, gold fill)
 *   Row 5+: Data rows
 *   Last row: optional TOTAL row (already included in data.rows)
 */
public final class ExcelExporter {

    private static final Logger log = LoggerFactory.getLogger(ExcelExporter.class);

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private ExcelExporter() {}

    /* =================================================================== */

    public static void export(ReportData data, File file) throws IOException {
        if (data == null) throw new IllegalArgumentException("No report data");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(shortenSheetName(data.title));

            int rowIdx = 0;

            // ---------- Title ----------
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(data.title);
            titleCell.setCellStyle(titleStyle(wb));

            // ---------- Subtitle ----------
            Row subtitleRow = sheet.createRow(rowIdx++);
            subtitleRow.createCell(0).setCellValue(nz(data.subtitle));

            // ---------- Filter label + generated on ----------
            Row metaRow = sheet.createRow(rowIdx++);
            StringBuilder meta = new StringBuilder();
            if (data.filterLabel != null && !data.filterLabel.isBlank()) {
                meta.append(data.filterLabel).append("     ");
            }
            meta.append("Generated: ").append(
                (data.generatedOn != null ? data.generatedOn : LocalDate.now())
                    .format(DATE_FMT));
            metaRow.createCell(0).setCellValue(meta.toString());

            // ---------- Blank row ----------
            rowIdx++;

            // ---------- Column headers ----------
            Row headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < data.columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(data.columns.get(i));
                cell.setCellStyle(headerStyle(wb));
            }

            // ---------- Data rows ----------
            CellStyle evenRow = dataRowStyle(wb, false);
            CellStyle oddRow  = dataRowStyle(wb, true);

            int lastRowIndex = rowIdx + data.rows.size();
            for (int r = 0; r < data.rows.size(); r++) {
                Row row = sheet.createRow(rowIdx + r);
                List<String> values = data.rows.get(r);

                // Detect a TOTAL row (first cell equals "TOTAL")
                boolean isTotal = !values.isEmpty()
                        && "TOTAL".equalsIgnoreCase(values.get(0));

                CellStyle style = isTotal ? totalRowStyle(wb)
                                          : (r % 2 == 0 ? evenRow : oddRow);

                for (int c = 0; c < values.size(); c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(nz(values.get(c)));
                    cell.setCellStyle(style);
                }
            }

            // ---------- Auto-size columns ----------
            int colCount = data.columns.size();
            for (int c = 0; c < colCount; c++) {
                sheet.autoSizeColumn(c);
                // Add a bit of breathing room
                int width = sheet.getColumnWidth(c);
                sheet.setColumnWidth(c, Math.min(width + 1200, 12000));
            }

            // ---------- Write file ----------
            try (FileOutputStream out = new FileOutputStream(file)) {
                wb.write(out);
            }

            log.info("Exported Excel report '{}' to {} ({} rows)",
                     data.title, file.getAbsolutePath(), data.rows.size());
        }
    }

    /* =================================================================== */
    /*  STYLES                                                             */
    /* =================================================================== */

    private static CellStyle titleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        s.setFont(f);
        return s;
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);

        s.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        s.setAlignment(HorizontalAlignment.LEFT);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private static CellStyle dataRowStyle(Workbook wb, boolean alternate) {
        CellStyle s = wb.createCellStyle();
        if (alternate) {
            s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        s.setBorderBottom(BorderStyle.HAIR);
        return s;
    }

    private static CellStyle totalRowStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.DOUBLE);
        s.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    /* =================================================================== */

    private static String nz(String s) { return s == null ? "" : s; }

    /** Excel sheet names have a 31-character limit and cannot contain / \ ? * [ ] : */
    private static String shortenSheetName(String name) {
        if (name == null) return "Report";
        String cleaned = name.replaceAll("[\\\\/?*\\[\\]:]", "").trim();
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }
}
