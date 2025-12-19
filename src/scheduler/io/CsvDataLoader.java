package scheduler.io;

import scheduler.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CsvDataLoader {

    public static List<Student> loadStudents(Path path) throws IOException {
        List<Student> result = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            boolean first = true;

            while ((line = br.readLine()) != null) {

                // boş satır
                if (line.isBlank())
                    continue;

                // header satırı (ilk satır)
                if (first) {
                    first = false;
                    continue;
                }

                // , veya ; destekle
                String[] parts = line.split("[,;]");
                if (parts.length == 0)
                    continue;

                // STUDENT ID her zaman ilk kolon
                String rawId = parts[0].trim();

                // 🔥 KRİTİK: Excel + CSV kaynaklı tüm kirleri temizle
                String cleanId = rawId
                        .replace("\"", "")
                        .replace("'", "")
                        .trim();

                if (!cleanId.isEmpty()) {
                    result.add(new Student(cleanId));
                }
            }
        }

        return result;
    }

    public static List<Course> loadCourses(Path path) throws IOException {
        List<Course> result = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                if (line.isBlank()) continue;

                String[] parts = line.split("[,;]");
                String id = normalizeCourseId(parts[0]);
                if (id.isEmpty()) continue;

                int duration = 90;
                if (parts.length >= 2) {
                    try { duration = Integer.parseInt(parts[1].trim()); }
                    catch (Exception ignored) {}
                }

                result.add(new Course(id, duration));
            }
        }
        return result;
    }

    public static List<Classroom> loadClassrooms(Path path) throws IOException {
        List<Classroom> result = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; }
                if (line.isBlank()) continue;

                String[] parts = line.split("[,;]");
                if (parts.length < 2) continue;

                try {
                    result.add(new Classroom(parts[0].trim(),
                            Integer.parseInt(parts[1].trim())));
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    public static List<Enrollment> loadEnrollments(Path path) throws IOException {
        List<Enrollment> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        List<String> lines = Files.readAllLines(path);
        String currentCourse = null;

        for (String rawLine : lines) {
            if (rawLine == null) continue;

            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Eğer satır '[' ile başlıyorsa → STUDENT LIST
            if (line.startsWith("[")) {
                if (currentCourse == null) continue;

                // Köşeli parantezleri kaldır
                line = line.substring(1, line.length() - 1);

                String[] students = line.split(",");

                for (String s : students) {
                    String sid = cleanStudentToken(s);
                    if (!sid.isEmpty()) {
                        String key = currentCourse + "||" + sid;
                        if (seen.add(key)) {
                            result.add(new Enrollment(sid, currentCourse));
                        }
                    }
                }
            }
            // Aksi halde → COURSE ID
            else {
                currentCourse = normalizeCourseId(line);
            }
        }

        System.out.println("Loaded enrollments: " + result.size());
        return result;
    }

    private static boolean looksLikeCourse(String s) {
        return s != null && s.matches("[A-Za-z].*");
    }

    private static String normalizeCourseId(String raw) {
        return raw == null ? "" : raw.replace("\"","").replace("'","").trim();
    }

    private static String cleanStudentToken(String raw) {
        if (raw == null) return "";
        return raw.replace("[","")
                .replace("]","")
                .replace("\"","")
                .replace("'","")
                .trim();
    }
}
