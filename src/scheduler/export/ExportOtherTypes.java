package scheduler.export;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

public class ExportOtherTypes {

    // CSV'de kullandığınız satır formatıyla birebir aynı olacak
    // Örn: [CourseCode, Date, Time, Rooms, StudentCount, Status]
    public static void exportExcel(List<String[]> rows, Path outputPath) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Schedule");

            // Optional header (matches your CSV columns)
            String[] header = new String[]{"CourseCode", "Date", "Time", "Rooms", "Students", "Status"};
            int r = 0;
            Row headerRow = sheet.createRow(r++);
            for (int c = 0; c < header.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(header[c]);
            }

            if (rows != null) {
                for (String[] rowData : rows) {
                    Row row = sheet.createRow(r++);
                    if (rowData == null) continue;
                    for (int c = 0; c < rowData.length; c++) {
                        String v = rowData[c] == null ? "" : rowData[c];
                        row.createCell(c).setCellValue(v);
                    }
                }
            }

            // Autosize the first 6 columns (safe even if rows have fewer cols)
            for (int c = 0; c < 6; c++) {
                sheet.autoSizeColumn(c);
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                wb.write(fos);
            }
        }
    }

    public static void exportPdf(List<String[]> rows, Path outputPath) throws Exception {
        if (outputPath == null) throw new IllegalArgumentException("outputPath is null");

        // Determine column count
        int cols = 1;
        if (rows != null) {
            for (String[] r : rows) {
                if (r != null) cols = Math.max(cols, r.length);
            }
        }

        // Build PDF in memory first to avoid partial/invalid files
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
        Document doc = new Document();
        PdfWriter writer = null;

        try {
            writer = PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Paragraph title = new Paragraph("Exam Schedule", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(cols);
            table.setWidthPercentage(100);

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            // If caller provided a header row, use it; otherwise use a generic header
            boolean hasHeader = (rows != null && !rows.isEmpty() && rows.get(0) != null);
            if (hasHeader) {
                String[] header = rows.get(0);
                for (int c = 0; c < cols; c++) {
                    String h = (c < header.length && header[c] != null) ? header[c] : "";
                    PdfPCell hc = new PdfPCell(new com.lowagie.text.Phrase(h, headerFont));
                    hc.setHorizontalAlignment(Element.ALIGN_CENTER);
                    hc.setPadding(4f);
                    table.addCell(hc);
                }
            } else {
                for (int c = 0; c < cols; c++) {
                    PdfPCell hc = new PdfPCell(new com.lowagie.text.Phrase("Col" + (c + 1), headerFont));
                    hc.setHorizontalAlignment(Element.ALIGN_CENTER);
                    hc.setPadding(4f);
                    table.addCell(hc);
                }
            }

            // Data rows (skip header if present)
            int startIdx = hasHeader ? 1 : 0;
            if (rows != null) {
                for (int i = startIdx; i < rows.size(); i++) {
                    String[] row = rows.get(i);
                    if (row == null) continue;
                    for (int c = 0; c < cols; c++) {
                        String v = (c < row.length && row[c] != null) ? row[c] : "";
                        PdfPCell cc = new PdfPCell(new com.lowagie.text.Phrase(v, cellFont));
                        cc.setPadding(3f);
                        table.addCell(cc);
                    }
                }
            }

            doc.add(table);

            // Finalize PDF into memory
            doc.close();
            if (writer != null) writer.close();

            // Write finalized bytes to disk
            Files.write(outputPath, baos.toByteArray());

        } finally {
            try { if (doc.isOpen()) doc.close(); } catch (Exception ignored) {}
            try { if (writer != null) writer.close(); } catch (Exception ignored) {}
            try { baos.close(); } catch (Exception ignored) {}
        }
    }


}