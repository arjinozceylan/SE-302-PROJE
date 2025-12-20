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
        // Supports two formats:
        // A) Pair CSV: Std_ID_001;CourseCode_01 (comma/semicolon/tab)
        // B) Instructor attendance list: CourseCode_01 line followed by lines containing many Std_ID_###

        List<Enrollment> out = new ArrayList<>();

        java.util.regex.Pattern pStudent = java.util.regex.Pattern.compile("Std_ID_\\d+", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Pattern pCourse = java.util.regex.Pattern.compile("CourseCode_\\d+", java.util.regex.Pattern.CASE_INSENSITIVE);

        String currentCourse = null;

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line == null) continue;
                String t = stripBom(line).trim();
                if (t.isEmpty()) continue;

                String lower = t.toLowerCase();
                if (lower.startsWith("all of") || lower.contains("attendance lists") || lower.contains("students in the system")) {
                    continue;
                }

                String norm = t.replace('\t', ';').replace(',', ';');

                // 1) Course header detection (course code anywhere on line)
                java.util.regex.Matcher mc = pCourse.matcher(norm);
                if (mc.find()) {
                    currentCourse = mc.group();

                    // Also parse any students that might be on the same line
                    java.util.regex.Matcher msInline = pStudent.matcher(norm);
                    while (msInline.find()) {
                        out.add(new Enrollment(msInline.group(), currentCourse));
                    }
                    continue;
                }

                // 2) Pair format (try first two columns)
                String[] parts = norm.split(";+");
                if (parts.length >= 2) {
                    String a = parts[0].trim().replace("\"", "").replace("'", "");
                    String b = parts[1].trim().replace("\"", "").replace("'", "");

                    java.util.regex.Matcher msa = pStudent.matcher(a);
                    java.util.regex.Matcher msb = pStudent.matcher(b);
                    java.util.regex.Matcher mca = pCourse.matcher(a);
                    java.util.regex.Matcher mcb = pCourse.matcher(b);

                    if (msa.find() && mcb.find()) {
                        out.add(new Enrollment(msa.group(), mcb.group()));
                        continue;
                    }
                    if (msb.find() && mca.find()) {
                        out.add(new Enrollment(msb.group(), mca.group()));
                        continue;
                    }
                }

                // 3) Attendance list lines under currentCourse
                if (currentCourse != null) {
                    java.util.regex.Matcher ms = pStudent.matcher(norm);
                    while (ms.find()) {
                        out.add(new Enrollment(ms.group(), currentCourse));
                    }
                }
            }
        }

        // Deduplicate
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Enrollment> result = new ArrayList<>();
        for (Enrollment e : out) {
            if (e == null) continue;
            String sid = e.getStudentId() == null ? "" : e.getStudentId().trim();
            String cid = e.getCourseId() == null ? "" : e.getCourseId().trim();
            if (sid.isEmpty() || cid.isEmpty()) continue;
            String key = (sid + "||" + cid).toLowerCase();
            if (seen.add(key)) {
                result.add(e);
            }
        }

        System.out.println("Loaded enrollments: " + result.size());
        return result;
    }

    private static String stripBom(String s) {
        if (s == null) return null;
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
        return cleanStudentId(raw);
    }
}
