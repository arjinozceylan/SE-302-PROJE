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

        // Old dataset: title + one ID per line (Std_ID_001)
        java.util.regex.Pattern pStdId = java.util.regex.Pattern.compile("Std_ID_\\d+", java.util.regex.Pattern.CASE_INSENSITIVE);
        // New dataset: header + rows (S000001;First;Last)
        java.util.regex.Pattern pSId = java.util.regex.Pattern.compile("S\\d{3,}", java.util.regex.Pattern.CASE_INSENSITIVE);

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line == null) continue;
                String t = stripBom(line).trim();
                if (t.isEmpty()) continue;

                String lower = t.toLowerCase();
                if (lower.startsWith("all of") || lower.startsWith("list of")) continue;

                // normalize delimiters
                String norm = t.replace('\t', ';').replace(',', ';');
                String[] parts = norm.split(";+");
                if (parts.length == 0) continue;

                String p0raw = stripBom(parts[0]).trim();
                if (p0raw.isEmpty()) continue;
                String p0 = p0raw.toLowerCase();

                // header lines
                if (p0.contains("student") && p0.contains("id")) continue;

                // handle weird placeholders like "..."
                if (p0raw.equals("...") || p0raw.equals("..")) continue;

                // Old single-column style: the line itself contains Std_ID_###
                java.util.regex.Matcher mOld = pStdId.matcher(norm);
                if (mOld.find()) {
                    String id = cleanStudentId(mOld.group());
                    if (!id.isEmpty()) {
                        result.add(new Student(id, ""));
                    }
                    continue;
                }

                // New style: first column is S000001
                if (pSId.matcher(p0raw).matches()) {
                    String id = cleanStudentId(p0raw);
                    if (id.isEmpty()) continue;

                    String name = "";
                    if (parts.length >= 3) {
                        name = (stripBom(parts[1]).trim() + " " + stripBom(parts[2]).trim()).trim();
                    } else if (parts.length == 2) {
                        String potentialName = stripBom(parts[1]).trim();
                        if (!potentialName.matches(".*\\d.*") && potentialName.length() > 1) {
                            name = potentialName;
                        }
                    }
                    name = name.replace("\"", "").replace("'", "").trim();
                    result.add(new Student(id, name));
                }
            }
        }

        System.out.println("Loaded students: " + result.size());
        return result;
    }

    public static List<Course> loadCourses(Path path) throws IOException {
        List<Course> result = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line == null) continue;
                String t = stripBom(line).trim();
                if (t.isEmpty()) continue;

                String lower = t.toLowerCase();
                if (lower.startsWith("all of") || lower.startsWith("list of")) continue;

                String norm = t.replace('\t', ';').replace(',', ';');
                String[] parts = norm.split(";+");
                if (parts.length == 0) continue;

                String p0 = stripBom(parts[0]).trim();
                if (p0.isEmpty()) continue;
                String p0l = p0.toLowerCase();
                if ((p0l.contains("course") && p0l.contains("id")) || p0l.contains("course code")) continue;

                String id = normalizeCourseId(p0);
                if (id.isEmpty()) continue;

                int duration = 90;
                if (parts.length >= 2) {
                    try { duration = Integer.parseInt(stripBom(parts[1]).trim()); } catch (Exception ignored) {}
                }

                result.add(new Course(id, duration));
            }
        }

        System.out.println("Loaded courses: " + result.size());
        return result;
    }

    public static List<Classroom> loadClassrooms(Path path) throws IOException {
        List<Classroom> result = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line == null) continue;
                String t = stripBom(line).trim();
                if (t.isEmpty()) continue;

                String lower = t.toLowerCase();
                if (lower.startsWith("all of") || lower.startsWith("list of")) continue;

                String norm = t.replace('\t', ';').replace(',', ';');
                String[] parts = norm.split(";+");
                if (parts.length < 2) continue;

                String roomId = stripBom(parts[0]).trim().replace("\"", "").replace("'", "");
                if (roomId.isEmpty()) continue;

                String roomIdLower = roomId.toLowerCase();
                String capCellLower = stripBom(parts[1]).trim().toLowerCase();

                // header like: classroom;capacity
                if ((roomIdLower.contains("classroom") || roomIdLower.contains("room")) && capCellLower.contains("capacity")) {
                    continue;
                }

                int cap;
                try { cap = Integer.parseInt(stripBom(parts[1]).trim()); }
                catch (Exception ignored) { continue; }

                result.add(new Classroom(roomId, cap));
            }
        }

        System.out.println("Loaded classrooms: " + result.size());
        return result;
    }

    public static List<Enrollment> loadEnrollments(Path path) throws IOException {
        // Supports:
        // A) Pair CSV: studentId;courseId (e.g., Std_ID_001;CourseCode_01 OR S000001;MATH351)
        // B) Attendance list: CourseCode_XX line then many student ids on following lines

        List<Enrollment> out = new ArrayList<>();

        java.util.regex.Pattern pStdId = java.util.regex.Pattern.compile("Std_ID_\\d+", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Pattern pCourseCode = java.util.regex.Pattern.compile("CourseCode_\\d+", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Pattern pSId = java.util.regex.Pattern.compile("S\\d{3,}", java.util.regex.Pattern.CASE_INSENSITIVE);

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

                // Course header in attendance format
                java.util.regex.Matcher mCourse = pCourseCode.matcher(norm);
                if (mCourse.find()) {
                    currentCourse = mCourse.group();

                    // add any students on same line
                    java.util.regex.Matcher msInline = pStdId.matcher(norm);
                    while (msInline.find()) {
                        out.add(new Enrollment(msInline.group(), currentCourse));
                    }
                    continue;
                }

                // Extract any student tokens on this line
                List<String> studentsOnLine = new ArrayList<>();
                java.util.regex.Matcher msOld = pStdId.matcher(norm);
                while (msOld.find()) studentsOnLine.add(msOld.group());
                java.util.regex.Matcher msNew = pSId.matcher(norm);
                while (msNew.find()) studentsOnLine.add(msNew.group());

                // If we're inside an attendance list, treat as attendance content
                if (currentCourse != null && !studentsOnLine.isEmpty()) {
                    for (String sid : studentsOnLine) {
                        out.add(new Enrollment(sid, currentCourse));
                    }
                    continue;
                }

                // Pair CSV parsing (first two columns)
                String[] rawParts = norm.split(";+");
                if (rawParts.length < 2) continue;

                String a = rawParts[0] == null ? "" : rawParts[0].trim();
                String b = rawParts[1] == null ? "" : rawParts[1].trim();

                a = a.replace("\"", "").replace("'", "").replace("[", "").replace("]", "").trim();
                b = b.replace("\"", "").replace("'", "").replace("[", "").replace("]", "").trim();

                if (a.isEmpty() || b.isEmpty()) continue;

                String aL = a.toLowerCase();
                String bL = b.toLowerCase();

                // header line
                boolean header = (aL.contains("student") && aL.contains("id")) && (bL.contains("course") && bL.contains("id"));
                if (header) continue;

                boolean aIsStudent = pStdId.matcher(a).find() || pSId.matcher(a).matches();
                boolean bIsStudent = pStdId.matcher(b).find() || pSId.matcher(b).matches();

                // Ignore garbage like: 1;Std_ID_439 (index + student) unless we also have a course
                if (!aIsStudent && bIsStudent && a.matches("\\d+")) {
                    continue;
                }

                // If both look like students, not a valid pair
                if (aIsStudent && bIsStudent) {
                    continue;
                }

                // Reverse old-format row
                boolean aIsOldCourse = pCourseCode.matcher(a).find();
                boolean bIsOldCourse = pCourseCode.matcher(b).find();

                String studentId = a;
                String courseId = b;

                if (aIsOldCourse && bIsStudent) {
                    studentId = b;
                    courseId = a;
                } else if (bIsOldCourse && aIsStudent) {
                    studentId = a;
                    courseId = b;
                }

                out.add(new Enrollment(studentId, courseId));
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
