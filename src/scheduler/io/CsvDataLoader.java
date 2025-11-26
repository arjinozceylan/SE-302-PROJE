package scheduler.io;

import scheduler.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CsvDataLoader {

    // 1) Öğrenciler: varsayım -> ilk sütun studentId
    public static List<Student> loadStudents(Path path) throws IOException {
        List<Student> result = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { // header satırını atla
                    first = false;
                    continue;
                }
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                String id = parts[0].trim();
                if (!id.isEmpty()) {
                    result.add(new Student(id));
                }
            }
        }
        return result;
    }

    // 2) Dersler: varsayım -> ilk sütun courseId, ikinci sütun durationMinutes
    public static List<Course> loadCourses(Path path) throws IOException {
        List<Course> result = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) {
                    // header satırını atla
                    first = false;
                    continue;
                }
                if (line.isBlank()) continue;

                String[] parts = line.split(",");
                if (parts.length < 1) {
                    continue; // boş/bozuk satır
                }

                String id = parts[0].trim();
                if (id.isEmpty()) {
                    continue;
                }

                // Şimdilik süre yoksa sabit bir değer kullanıyoruz (ör: 90 dk)
                int durationMinutes = 90;

                result.add(new Course(id, durationMinutes));
            }
        }
        return result;
    }

    // 3) Sınıflar: varsayım -> ilk sütun classroomId, ikinci sütun capacity
    public static List<Classroom> loadClassrooms(Path path) throws IOException {
        List<Classroom> result = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) {
                    // header satırını atla
                    first = false;
                    continue;
                }
                if (line.isBlank()) continue;

                // Virgül veya noktalı virgüle göre böl (hangi ayracı kullandıysan)
                String[] parts = line.split("[,;]");
                if (parts.length < 2) {
                    // Bu satırda kapasite yok, bozuk kabul edip atlıyoruz
                    continue;
                }

                String id = parts[0].trim();
                String capStr = parts[1].trim();
                if (id.isEmpty() || capStr.isEmpty()) {
                    continue;
                }

                int capacity;
                try {
                    capacity = Integer.parseInt(capStr);
                } catch (NumberFormatException e) {
                    // Sayıya çevrilemiyorsa bu satırı da atla
                    continue;
                }

                result.add(new Classroom(id, capacity));
            }
        }
        return result;
    }
    public static void debugPrintAttendance(Path path) throws IOException {
        System.out.println("=== DEBUG AllAttendanceLists.csv raw content ===");
        try (java.io.BufferedReader br = java.nio.file.Files.newBufferedReader(path)) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null && lineNo < 10) { // ilk 10 satır yeter
                System.out.println("LINE " + lineNo + ": " + line);
                lineNo++;
            }
        }
        System.out.println("=== END DEBUG ===");
    }

    // 4) Enrollments: varsayım -> attendance dosyasında
    //    ilk sütun studentId, ikinci sütun courseId
    // 4) Enrollments: attendance dosyası
    // 4) Enrollments: attendance dosyası (CourseCode satırı + liste satırı)
    public static List<Enrollment> loadEnrollments(Path path) throws IOException {
        List<Enrollment> result = new ArrayList<>();

        List<String> lines = java.nio.file.Files.readAllLines(path);

        for (int i = 0; i < lines.size(); i++) {
            String courseLine = lines.get(i).trim();

            // CourseCode satırını bul
            if (courseLine.startsWith("CourseCode_")) {

                String courseId = courseLine; // ör: "CourseCode_01"

                // Sonraki satır öğrenci listesi olmalı
                if (i + 1 < lines.size()) {
                    String listLine = lines.get(i + 1).trim();

                    // Ör: ['Std_ID_170', 'Std_ID_077', ...]
                    if (listLine.startsWith("[") && listLine.endsWith("]")) {

                        // Köşeli parantezleri kaldır
                        String inside = listLine.substring(1, listLine.length() - 1);

                        // Öğrencileri virgüllere göre böl
                        String[] items = inside.split(",");

                        for (String raw : items) {
                            String token = raw.trim();

                            // Tek tırnakları temizle
                            token = token.replace("'", "");

                            if (!token.isEmpty()) {
                                result.add(new Enrollment(token, courseId));
                            }
                        }
                    }
                }
            }
        }

        return result;
    }
}