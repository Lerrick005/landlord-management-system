package com.propertymanager.reports;

import com.propertymanager.database.Database;
import com.propertymanager.reports.ReportService.ReportData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes a ReportData to a clean PDF using Apache PDFBox.
 */
public final class PdfExporter {

    private static final Logger log = LoggerFactory.getLogger(PdfExporter.class);

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    /* ---- Page geometry (in points, 1pt = 1/72 inch) ---- */
    private static final float PAGE_WIDTH  = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    private static final float MARGIN_LEFT   = 40;
    private static final float MARGIN_RIGHT  = 40;
    private static final float MARGIN_TOP    = 50;
    private static final float MARGIN_BOTTOM = 50;

    private static final float CONTENT_WIDTH =
        PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;

    private static final float FONT_SIZE_BODY   = 9f;
    private static final float FONT_SIZE_HEADER = 9f;
    private static final float FONT_SIZE_TITLE  = 16f;
    private static final float FONT_SIZE_SUB    = 10f;

    private static final float ROW_HEIGHT = 18f;

    /* ---- Colors ---- */
    private static final Color COLOR_HEADER_FILL = new Color(201, 162, 74);
    private static final Color COLOR_HEADER_TEXT = new Color(15, 17, 21);
    private static final Color COLOR_ALT_ROW     = new Color(247, 245, 240);
    private static final Color COLOR_TEXT        = new Color(23, 26, 32);
    private static final Color COLOR_MUTED       = new Color(92, 92, 88);
    private static final Color COLOR_LINE        = new Color(217, 215, 209);

    private PdfExporter() {}

    /* =================================================================== */

    public static void export(ReportData data, File file) throws IOException {
        if (data == null) throw new IllegalArgumentException("No report data");

        try (PDDocument doc = new PDDocument()) {
            PDFont fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            List<Float> colWidths = computeColumnWidths(data, fontBold, fontRegular);
            List<List<List<String>>> pages = paginateRows(data.rows, 40);

            int totalPages = pages.size();
            for (int pageNum = 0; pageNum < totalPages; pageNum++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    float y = PAGE_HEIGHT - MARGIN_TOP;

                    y = drawHeader(cs, data, fontBold, fontRegular, y);
                    y = drawColumnHeaders(cs, data.columns, colWidths, fontBold, y);

                    List<List<String>> pageRows = pages.get(pageNum);
                    y = drawRows(cs, pageRows, colWidths, fontRegular, y);

                    drawFooter(cs, fontRegular, pageNum + 1, totalPages);
                }
            }

            doc.save(file);

            log.info("Exported PDF report '{}' to {} ({} rows, {} pages)",
                     data.title, file.getAbsolutePath(),
                     data.rows.size(), totalPages);
        }
    }

    /* =================================================================== */
    /*  HEADER BLOCK                                                       */
    /* =================================================================== */

    private static float drawHeader(PDPageContentStream cs, ReportData data,
                                    PDFont fontBold, PDFont fontRegular,
                                    float yStart) throws IOException {

        float y = yStart;

        String orgName = readSetting("org.name", "Property Management");
        writeText(cs, fontBold, 11f, COLOR_TEXT, MARGIN_LEFT, y, orgName);
        y -= 14;

        String orgAddress = readSetting("org.address", "");
        if (!orgAddress.isBlank()) {
            writeText(cs, fontRegular, 9f, COLOR_MUTED, MARGIN_LEFT, y, orgAddress);
            y -= 12;
        }

        y -= 4;
        cs.setStrokingColor(COLOR_HEADER_FILL);
        cs.setLineWidth(1.2f);
        cs.moveTo(MARGIN_LEFT, y);
        cs.lineTo(MARGIN_LEFT + CONTENT_WIDTH, y);
        cs.stroke();
        y -= 22;

        writeText(cs, fontBold, FONT_SIZE_TITLE, COLOR_TEXT, MARGIN_LEFT, y, data.title);
        y -= 18;

        if (data.subtitle != null && !data.subtitle.isBlank()) {
            writeText(cs, fontRegular, FONT_SIZE_SUB, COLOR_MUTED,
                      MARGIN_LEFT, y, data.subtitle);
            y -= 14;
        }

        if (data.filterLabel != null && !data.filterLabel.isBlank()) {
            writeText(cs, fontBold, 9f, COLOR_TEXT, MARGIN_LEFT, y, data.filterLabel);
            y -= 12;
        }

        String genOn = "Generated: "
            + (data.generatedOn != null ? data.generatedOn : LocalDate.now())
                .format(DATE_FMT);
        float genWidth = fontRegular.getStringWidth(genOn) / 1000f * 9f;
        writeText(cs, fontRegular, 9f, COLOR_MUTED,
                  PAGE_WIDTH - MARGIN_RIGHT - genWidth, y + 26, genOn);

        y -= 10;

        return y;
    }

    /* =================================================================== */
    /*  COLUMN HEADERS                                                     */
    /* =================================================================== */

    private static float drawColumnHeaders(PDPageContentStream cs,
                                           List<String> columns,
                                           List<Float> colWidths,
                                           PDFont fontBold,
                                           float yStart) throws IOException {
        float y = yStart;
        float rowTop = y + 5;
        float rowBottom = y - ROW_HEIGHT + 5;

        cs.setNonStrokingColor(COLOR_HEADER_FILL);
        cs.addRect(MARGIN_LEFT, rowBottom, CONTENT_WIDTH, rowTop - rowBottom);
        cs.fill();

        float x = MARGIN_LEFT + 6;
        for (int i = 0; i < columns.size(); i++) {
            String header = columns.get(i);
            if (header.length() > 20) header = header.substring(0, 18) + "...";
            writeText(cs, fontBold, FONT_SIZE_HEADER, COLOR_HEADER_TEXT,
                      x, rowBottom + 6, header);
            x += colWidths.get(i);
        }

        return rowBottom - 4;
    }

    /* =================================================================== */
    /*  DATA ROWS                                                          */
    /* =================================================================== */

    private static float drawRows(PDPageContentStream cs,
                                  List<List<String>> rows,
                                  List<Float> colWidths,
                                  PDFont fontRegular,
                                  float yStart) throws IOException {
        float y = yStart;

        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            boolean isTotal = !row.isEmpty() && "TOTAL".equalsIgnoreCase(row.get(0));
            boolean alternate = (r % 2 == 1);

            float rowBottom = y - ROW_HEIGHT;

            if (isTotal) {
                cs.setNonStrokingColor(250, 245, 220);
                cs.addRect(MARGIN_LEFT, rowBottom, CONTENT_WIDTH, ROW_HEIGHT);
                cs.fill();
                cs.setStrokingColor(COLOR_HEADER_FILL);
                cs.setLineWidth(0.8f);
                cs.moveTo(MARGIN_LEFT, rowBottom + ROW_HEIGHT);
                cs.lineTo(MARGIN_LEFT + CONTENT_WIDTH, rowBottom + ROW_HEIGHT);
                cs.stroke();
            } else if (alternate) {
                cs.setNonStrokingColor(COLOR_ALT_ROW);
                cs.addRect(MARGIN_LEFT, rowBottom, CONTENT_WIDTH, ROW_HEIGHT);
                cs.fill();
            }

            float x = MARGIN_LEFT + 6;
            for (int c = 0; c < row.size(); c++) {
                String value = row.get(c);
                if (value == null) value = "";

                value = truncateToWidth(value, fontRegular, FONT_SIZE_BODY,
                                        colWidths.get(c) - 10);

                writeText(cs, fontRegular, FONT_SIZE_BODY, COLOR_TEXT,
                          x, rowBottom + 5, value);
                x += colWidths.get(c);
            }

            y = rowBottom - 1;
        }

        return y;
    }

    /* =================================================================== */
    /*  FOOTER                                                             */
    /* =================================================================== */

    private static void drawFooter(PDPageContentStream cs, PDFont fontRegular,
                                   int pageNum, int totalPages) throws IOException {
        String pageText = "Page " + pageNum + " of " + totalPages;
        float width = fontRegular.getStringWidth(pageText) / 1000f * 8f;
        writeText(cs, fontRegular, 8f, COLOR_MUTED,
                  PAGE_WIDTH - MARGIN_RIGHT - width,
                  MARGIN_BOTTOM - 20, pageText);
    }

    /* =================================================================== */
    /*  HELPERS                                                            */
    /* =================================================================== */

    private static void writeText(PDPageContentStream cs, PDFont font,
                                  float size, Color color,
                                  float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(text));
        cs.endText();
    }

    /**
     * PDFBox Standard14 fonts don't support many Unicode characters.
     * Replaces them with ASCII equivalents using escape sequences so this
     * source file stays pure ASCII and compiles cleanly.
     */
    private static String sanitize(String text) {
        if (text == null) return "";
        return text
            .replace('\u2014', '-')     // em dash
            .replace('\u2013', '-')     // en dash
            .replace('\u2018', '\'')    // left single quote
            .replace('\u2019', '\'')    // right single quote
            .replace('\u201C', '"')     // left double quote
            .replace('\u201D', '"')     // right double quote
            .replace('\u2026', '.')     // ellipsis (as single dot, safer)
            .replace('\u00B7', '*')     // middle dot
            .replace('\u2713', 'v');    // checkmark
    }

    private static String truncateToWidth(String text, PDFont font,
                                          float size, float maxWidth) {
        if (text == null) return "";
        try {
            float w = font.getStringWidth(sanitize(text)) / 1000f * size;
            if (w <= maxWidth) return text;

            String s = text;
            while (s.length() > 1) {
                s = s.substring(0, s.length() - 1);
                w = font.getStringWidth(sanitize(s) + "...") / 1000f * size;
                if (w <= maxWidth) return s + "...";
            }
            return s;
        } catch (IOException e) {
            return text;
        }
    }

    private static List<Float> computeColumnWidths(ReportData data,
                                                   PDFont fontBold, PDFont fontRegular) {
        int n = data.columns.size();
        List<Float> widths = new ArrayList<>();
        if (n == 0) return widths;

        float[] desired = new float[n];
        for (int c = 0; c < n; c++) {
            try {
                desired[c] = fontBold.getStringWidth(data.columns.get(c))
                             / 1000f * FONT_SIZE_HEADER;
            } catch (IOException e) { desired[c] = 60; }
        }
        for (List<String> row : data.rows) {
            for (int c = 0; c < row.size() && c < n; c++) {
                try {
                    float w = fontRegular.getStringWidth(nvl(row.get(c)))
                              / 1000f * FONT_SIZE_BODY;
                    if (w > desired[c]) desired[c] = w;
                } catch (IOException ignored) {}
            }
        }

        float totalDesired = 0;
        for (int c = 0; c < n; c++) {
            desired[c] += 14;
            totalDesired += desired[c];
        }

        float scale = CONTENT_WIDTH / totalDesired;
        for (int c = 0; c < n; c++) {
            widths.add(desired[c] * scale);
        }
        return widths;
    }

    private static String nvl(String s) { return s == null ? "" : s; }

    private static List<List<List<String>>> paginateRows(List<List<String>> rows,
                                                          int rowsPerPage) {
        List<List<List<String>>> pages = new ArrayList<>();
        if (rows.isEmpty()) {
            pages.add(new ArrayList<>());
            return pages;
        }
        int totalPages = (int) Math.ceil(rows.size() / (double) rowsPerPage);
        for (int i = 0; i < totalPages; i++) {
            int start = i * rowsPerPage;
            int end = Math.min(start + rowsPerPage, rows.size());
            pages.add(new ArrayList<>(rows.subList(start, end)));
        }
        return pages;
    }

    private static String readSetting(String key, String fallback) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT value FROM system_settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    return (v != null && !v.isBlank()) ? v : fallback;
                }
            }
        } catch (SQLException e) {
            log.warn("readSetting({}) failed", key, e);
        }
        return fallback;
    }
}
