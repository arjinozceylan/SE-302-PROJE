package scheduler.export;

import com.lowagie.text.Document;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.nio.file.Path;
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
        // Şimdilik boş
    }

    public static void exportWord(List<String[]> rows, Path outputPath) throws Exception {
        // Şimdilik boş
    }
}