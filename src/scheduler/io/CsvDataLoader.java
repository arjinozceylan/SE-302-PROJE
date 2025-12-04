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
    // 2) Dersler: varsayım -> ilk sütun courseId, ikinci sütun durationMinutes
    // 2) Dersler: varsayım -> ilk sütun courseId, ikinci sütun durationMinutes
    // 2) Dersler: varsayım -> ilk sütun courseId, ikinci sütun durationMinutes
    public static List<Course> loadCourses(Path path) throws IOException {
        List<Course> result = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) {
                    first = false; // header
                    continue;
                }
                if (line.isBlank()) continue;

                String[] parts = line.split("[,;]");
                if (parts.length < 1) continue;

                // ESKİ: String id = parts[0].trim();
                String id = normalizeCourseId(parts[0]);
                if (id.isEmpty()) continue;

                int durationMinutes = 90;
                if (parts.length >= 2) {
                    String durStr = parts[1].trim();
                    if (!durStr.isEmpty()) {
                        try {
                            durationMinutes = Integer.parseInt(durStr);
                        } catch (NumberFormatException ignore) {}
                    }
                }

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
    // Ders ID'sini normalize eder (gereksiz boşluk, tırnak vs. temizler)
    private static String normalizeCourseId(String raw) {
        if (raw == null) return "";
        String id = raw.trim();

        // "'CourseCode_01'" gibi durumlar varsa temizle
        id = id.replace("'", "")
                .replace("\"", "")
                .trim();

        return id;
    }

    // 4) Enrollments: varsayım -> attendance dosyasında
    //    ilk sütun studentId, ikinci sütun courseId
    // 4) Enrollments: attendance dosyası
    // 4) Enrollments: attendance dosyası (CourseCode satırı + liste satırı)
    // 4) Enrollments: attendance dosyası
// Format: her satır = 1 ders
//   ilk hücre: CourseCode_XX
//   devamı: öğrenciler ('Std_ID_001' vb.)
    // 4) Enrollments: attendance dosyası
// Desteklenen formatlar:
// 1) Eski format:
//    CourseCode_01
//    ['Std_ID_001','Std_ID_002', ...]
//
// 2) Yeni format (Numbers/Excel):
//    CourseCode_01, Std_ID_001, Std_ID_002, ...
//    veya
//    CourseCode_01
//    Std_ID_001, Std_ID_002, ...
    public static List<Enrollment> loadEnrollments(Path path) throws IOException {
        List<Enrollment> result = new ArrayList<>();
        // (courseId, studentId) çiftlerini şurada hatırlayacağız
        java.util.Set<String> seen = new java.util.HashSet<>();

        List<String> lines = java.nio.file.Files.readAllLines(path);
        String currentCourse = null;

        for (String rawLine : lines) {
            if (rawLine == null) continue;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("[,;]");
            if (parts.length == 0) continue;

            String first = parts[0].trim();
            if (first.isEmpty()) continue;

            // Ders satırı
            if (first.startsWith("CourseCode_") || first.startsWith("Course_")) {
                currentCourse = normalizeCourseId(first);

                // Aynı satırda öğrenciler de varsa
                for (int i = 1; i < parts.length; i++) {
                    String sid = cleanStudentToken(parts[i]);
                    if (!sid.isEmpty()) {
                        String key = currentCourse + "||" + sid;
                        if (seen.add(key)) { // daha önce eklenmediyse
                            result.add(new Enrollment(sid, currentCourse));
                        }
                    }
                }
            }
            // CourseCode satırından sonra gelen, yeni ders başlamayan satırlar
            else if (currentCourse != null) {
                for (String part : parts) {
                    String sid = cleanStudentToken(part);
                    if (!sid.isEmpty()) {
                        String key = currentCourse + "||" + sid;
                        if (seen.add(key)) {
                            result.add(new Enrollment(sid, currentCourse));
                        }
                    }
                }
            }
        }

        System.out.println("DEBUG loadEnrollments: loaded " + result.size() + " unique enrollments");
        return result;
    }

    // Küçük yardımcı metod (CsvDataLoader içinde private olarak ekle)
    private static String cleanStudentToken(String raw) {
        if (raw == null) return "";
        String token = raw.trim();
        if (token.isEmpty()) return "";

        // Köşeli parantez ve tırnakları temizle: ['Std_ID_001'] gibi durumlar için
        if (token.startsWith("[")) token = token.substring(1);
        if (token.endsWith("]")) token = token.substring(0, token.length() - 1);

        token = token.replace("'", "")
                .replace("\"", "")
                .trim();

        return token;
    }
}