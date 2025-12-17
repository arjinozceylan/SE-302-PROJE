package scheduler.io;

import scheduler.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CsvDataLoader {


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
                if (line.isBlank())
                    continue;
                String[] parts = line.split(",");
                String id = parts[0].trim();
                if (!id.isEmpty()) {
                    result.add(new Student(id));
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
                if (first) {
                    first = false; // header
                    continue;
                }
                if (line.isBlank())
                    continue;

                String[] parts = line.split("[,;]");
                if (parts.length < 1)
                    continue;


                String id = normalizeCourseId(parts[0]);
                if (id.isEmpty())
                    continue;

                int durationMinutes = 90;
                if (parts.length >= 2) {
                    String durStr = parts[1].trim();
                    if (!durStr.isEmpty()) {
                        try {
                            durationMinutes = Integer.parseInt(durStr);
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }

                result.add(new Course(id, durationMinutes));
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
                if (first) {
                    // header satırını atla
                    first = false;
                    continue;
                }
                if (line.isBlank())
                    continue;

                String[] parts = line.split("[,;]");
                if (parts.length < 2) {

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
        if (raw == null)
            return "";
        String id = raw.trim();


        id = id.replace("'", "")
                .replace("\"", "")
                .trim();

        return id;
    }

    public static List<Enrollment> loadEnrollments(Path path) throws IOException {
        List<Enrollment> result = new ArrayList<>();
        // (courseId, studentId) çiftlerini şurada hatırlayacağız
        java.util.Set<String> seen = new java.util.HashSet<>();

        List<String> lines = java.nio.file.Files.readAllLines(path);
        String currentCourse = null;

        for (String rawLine : lines) {
            if (rawLine == null)
                continue;
            String line = rawLine.trim();
            if (line.isEmpty())
                continue;

            String[] parts = line.split("[,;]");
            if (parts.length == 0)
                continue;

            String first = parts[0].trim();
            if (first.isEmpty())
                continue;


            if (first.startsWith("CourseCode_") || first.startsWith("Course_")) {
                currentCourse = normalizeCourseId(first);


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

    private static String cleanStudentToken(String raw) {
        if (raw == null)
            return "";
        String token = raw.trim();
        if (token.isEmpty())
            return "";

        if (token.startsWith("["))
            token = token.substring(1);
        if (token.endsWith("]"))
            token = token.substring(0, token.length() - 1);

        token = token.replace("'", "")
                .replace("\"", "")
                .trim();

        return token;
    }
}