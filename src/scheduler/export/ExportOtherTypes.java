package scheduler.export;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.lowagie.text.Document;

import java.nio.file.Path;
import java.util.List;

public class ExportOtherTypes {

    // CSV'de kullandığınız satır formatıyla birebir aynı olacak
    // Örn: [CourseCode, Date, Time, Rooms, StudentCount, Status]
    public static void exportExcel(List<String[]> rows, Path outputPath) throws Exception {
        // Şimdilik boş
    }

    public static void exportPdf(List<String[]> rows, Path outputPath) throws Exception {
        // Şimdilik boş
    }

    public static void exportWord(List<String[]> rows, Path outputPath) throws Exception {
        // Şimdilik boş
    }
}