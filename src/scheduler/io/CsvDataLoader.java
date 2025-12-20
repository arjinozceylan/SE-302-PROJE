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

            while ((line = br.readLine()) != null) {
                if (line == null) continue;
                if (line.isBlank()) continue;

                // , ; veya tab destekle
                String[] parts = line.split("[;,\t]");
                if (parts.length == 0) continue;

                // --- FİLTRELEME (Gereksiz Başlıkları Atla) ---
                String p0 = stripBom(parts[0]).trim().toLowerCase();

                // 1. Header satırı: "student id" veya "std_id" gibi başlıklar
                if (p0.contains("student") && p0.contains("id")) {
                    continue;
                }
                if (p0.contains("std_") && p0.contains("id")) {
                    continue;
                }

                // 2. Dosya Başlığı: "all of the students..." gibi rapor başlıkları
                if (p0.startsWith("all of the students") || p0.startsWith("list of")) {
                    continue;
                }

                // 3. Uzunluk Kontrolü: ID sütunu bir cümle olamaz (örn > 40 karakterse atla)
                if (p0.length() > 40) {
                    continue;
                }
                // ---------------------------------------------

                // 1. Sütun: ID
                String rawId = stripBom(parts[0]).trim();
                String cleanId = cleanStudentId(rawId);
                if (cleanId.isEmpty()) continue;

                // 2. ve 3. Sütun: İsim Okuma Mantığı
                String name = "";
                if (parts.length >= 3) {
                    // Format: ID, Ad, Soyad
                    name = parts[1].trim() + " " + parts[2].trim();
                } else if (parts.length == 2) {
                    // Format: ID, Ad Soyad (veya sadece Ad)
                    String potentialName = parts[1].trim();
                    // Basit kontrol: İkinci sütun sayısal değilse isim kabul et
                    if (!potentialName.matches(".*\\d.*") && potentialName.length() > 1) { 
                        name = potentialName;
                    }
                }
                
                // Temizlik
                name = name.replace("\"", "").replace("'", "").trim();

                result.add(new Student(cleanId, name));
            }
        }

        System.out.println("Loaded students: " + result.size());
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
                String id = normalizeCourseId(stripBom(parts[0]));
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
                    String roomId = stripBom(parts[0]).trim();
                    int cap = Integer.parseInt(parts[1].trim());
                    if (!roomId.isEmpty()) {
                        result.add(new Classroom(roomId, cap));
                    }
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    public static List<Enrollment> loadEnrollments(Path path) throws IOException {
        List<Enrollment> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        List<String> lines = Files.readAllLines(path);

        // Detect format:
        // A) Pair CSV: header like "studentId;courseId" and rows like "S000001;MATH351"
        // B) Legacy bracket format: COURSE_ID line followed by "[Std_ID_1, Std_ID_2, ...]"
        boolean looksLikePairCsv = false;
        for (String rawLine : lines) {
            if (rawLine == null) continue;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            line = stripBom(line);

            // If it contains a delimiter and at least 2 tokens, treat as pair CSV.
            String[] parts = line.split("[,;]");
            if (parts.length >= 2) {
                String a = parts[0].trim();
                String b = parts[1].trim();
                // header or data both acceptable
                if (!a.isEmpty() && !b.isEmpty()) {
                    looksLikePairCsv = true;
                }
            }
            break;
        }

        if (looksLikePairCsv) {
            boolean first = true;
            for (String rawLine : lines) {
                if (rawLine == null) continue;
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                line = stripBom(line);

                // Skip header
                if (first) {
                    first = false;
                    continue;
                }

                String[] parts = line.split("[,;]");
                if (parts.length < 2) continue;

                String studentId = cleanStudentId(parts[0]);
                String courseId = normalizeCourseId(parts[1]);
                if (studentId.isEmpty() || courseId.isEmpty()) continue;

                String key = courseId + "||" + studentId;
                if (seen.add(key)) {
                    result.add(new Enrollment(studentId, courseId));
                }
            }

            System.out.println("Loaded enrollments: " + result.size());
            return result;
        }

        // Legacy bracket format
        String currentCourse = null;
        for (String rawLine : lines) {
            if (rawLine == null) continue;

            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            line = stripBom(line);

            if (line.startsWith("[")) {
                if (currentCourse == null) continue;

                // remove brackets
                if (line.endsWith("]") && line.length() >= 2) {
                    line = line.substring(1, line.length() - 1);
                } else {
                    line = line.substring(1);
                }

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
            } else {
                currentCourse = normalizeCourseId(line);
            }
        }

        System.out.println("Loaded enrollments: " + result.size());
        return result;
    }

    private static String stripBom(String s) {
        if (s == null) return null;
        // UTF-8 BOM
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }

    private static String normalizeCourseId(String raw) {
        if (raw == null) return "";
        String s = stripBom(raw);
        return s.replace("\"", "")
                .replace("'", "")
                .trim();
    }

    private static String cleanStudentId(String raw) {
        if (raw == null) return "";
        String s = stripBom(raw);
        return s.replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .replace("'", "")
                .trim();
    }

    private static String cleanStudentToken(String raw) {
        // Backward compatibility (old enrollment format)
        return cleanStudentId(raw);
    }
}
