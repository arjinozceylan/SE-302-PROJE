package scheduler.dao;

import java.sql.*;
import java.util.*;
import scheduler.model.*;

public class DBManager {

    private static final String DB_URL = "jdbc:sqlite:scheduler.db";

    public static void initializeDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL);
                Statement st = conn.createStatement()) {

            // --- 1. VERİ TABLOLARI ---
            st.execute("CREATE TABLE IF NOT EXISTS students (id TEXT PRIMARY KEY)");
            st.execute(
                    "CREATE TABLE IF NOT EXISTS courses (id TEXT PRIMARY KEY, duration INTEGER, durationMinutes INTEGER DEFAULT 0, minRoomCapacity INTEGER DEFAULT 0, maxRoomCapacity INTEGER DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS classrooms (id TEXT PRIMARY KEY, capacity INTEGER)");
            st.execute("CREATE TABLE IF NOT EXISTS enrollments (student_id TEXT, course_id TEXT)");
            st.execute(
                    "CREATE TABLE IF NOT EXISTS schedule (student_id TEXT, course_id TEXT, date TEXT, start_time TEXT, end_time TEXT, room TEXT, seat INTEGER)");
            st.execute(
                    "CREATE TABLE IF NOT EXISTS conflict_log (id INTEGER PRIMARY KEY AUTOINCREMENT, course_id TEXT, reason TEXT)");

            // --- 2. AYAR VE DOSYA TABLOLARI (Persistence) ---
            st.execute("CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY, value TEXT)");
            st.execute("CREATE TABLE IF NOT EXISTS saved_files (path TEXT PRIMARY KEY, type TEXT, active INTEGER)");
            st.execute(
                    "CREATE TABLE IF NOT EXISTS rule_groups (group_id INTEGER, course_id TEXT, duration INTEGER, min_cap INTEGER, max_cap INTEGER)");

            // --- 3. EXPORT İÇİN UPLOADED FILES TABLOSU (Eski kod uyumu için) ---
            st.execute("CREATE TABLE IF NOT EXISTS uploaded_files (filename TEXT PRIMARY KEY)");
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    // =============================================================
    // AYARLARI YÖNETME
    // =============================================================

    public static void saveSetting(String key, String value) {
        String sql = "INSERT OR REPLACE INTO app_settings(key, value) VALUES(?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static String loadSetting(String key) {
        String sql = "SELECT value FROM app_settings WHERE key = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getString("value");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // =============================================================
    // DOSYA DURUMLARINI YÖNETME
    // =============================================================

    public static void clearSavedFiles() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM saved_files");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void saveFileState(String path, String type, boolean isActive) {
        String sql = "INSERT OR REPLACE INTO saved_files(path, type, active) VALUES(?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.setString(2, type);
            ps.setInt(3, isActive ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<SavedFileRecord> loadFileStates() {
        List<SavedFileRecord> list = new ArrayList<>();
        String sql = "SELECT path, type, active FROM saved_files";
        try (Connection conn = getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new SavedFileRecord(
                        rs.getString("path"),
                        rs.getString("type"),
                        rs.getInt("active") == 1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // =============================================================
    // KURAL GRUPLARINI YÖNETME
    // =============================================================

    public static void clearRules() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM rule_groups");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void saveRule(int groupId, String courseId, int duration, int minCap, int maxCap) {
        String sql = "INSERT INTO rule_groups(group_id, course_id, duration, min_cap, max_cap) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setString(2, courseId);
            ps.setInt(3, duration);
            ps.setInt(4, minCap);
            ps.setInt(5, maxCap);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<RuleRecord> loadRules() {
        List<RuleRecord> list = new ArrayList<>();
        String sql = "SELECT group_id, course_id, duration, min_cap, max_cap FROM rule_groups";
        try (Connection conn = getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new RuleRecord(
                        rs.getInt("group_id"),
                        rs.getString("course_id"),
                        rs.getInt("duration"),
                        rs.getInt("min_cap"),
                        rs.getInt("max_cap")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // =============================================================
    // SCHEDULE / VERİ YÖNETİMİ (Eski metodlar + Export)
    // =============================================================

    public static void clearScheduleTable() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM schedule");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void clearConflictLog() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM conflict_log");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void insertSchedule(StudentExam se) {
        String sql = "INSERT INTO schedule(student_id, course_id, date, start_time, end_time, room, seat) VALUES(?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, se.getStudentId());
            ps.setString(2, se.getCourseId());
            ps.setString(3, se.getTimeslot().getDate().toString());
            ps.setString(4, se.getTimeslot().getStart().toString());
            ps.setString(5, se.getTimeslot().getEnd().toString());
            ps.setString(6, se.getClassroomId());
            ps.setInt(7, se.getSeatNo());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean exportScheduleToCSV(String filePath) {
        String sql = "SELECT student_id, course_id, date, start_time, end_time, room, seat FROM schedule";

        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery();
                java.io.PrintWriter writer = new java.io.PrintWriter(filePath)) {

            // CSV header
            writer.println("Student ID,Course ID,Date,Start,End,Room,Seat");

            while (rs.next()) {
                String line = String.join(",",
                        rs.getString("student_id"),
                        rs.getString("course_id"),
                        rs.getString("date"),
                        rs.getString("start_time"),
                        rs.getString("end_time"),
                        rs.getString("room"),
                        String.valueOf(rs.getInt("seat")));
                writer.println(line);
            }
            return true;

        } catch (Exception e) {
            System.err.println("EXPORT ERROR: " + e.getMessage());
            return false;
        }
    }

    public static List<Student> loadStudentsFromDB() {
        List<Student> list = new ArrayList<>();
        String sql = "SELECT id FROM students";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Student(rs.getString("id")));
            }
        } catch (SQLException e) {
            System.err.println("DB LOAD STUDENTS ERROR: " + e.getMessage());
        }
        return list;
    }

    public static List<Classroom> loadClassroomsFromDB() {
        List<Classroom> list = new ArrayList<>();
        String sql = "SELECT id, capacity FROM classrooms";
        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Classroom(rs.getString("id"), rs.getInt("capacity")));
            }
        } catch (SQLException e) {
            System.err.println("DB LOAD CLASSROOMS ERROR: " + e.getMessage());
        }
        return list;
    }

    public static List<Course> loadCoursesFromDB() {
        List<Course> list = new ArrayList<>();
        String sql = "SELECT id, duration, durationMinutes, minRoomCapacity, maxRoomCapacity FROM courses";

        try (Connection conn = getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                int dur = rs.getInt("durationMinutes");
                if (dur == 0)
                    dur = rs.getInt("duration");
                if (dur == 0)
                    dur = 90;

                int minCap = rs.getInt("minRoomCapacity");
                int maxCap = rs.getInt("maxRoomCapacity");

                Course c = new Course(id, dur);
                c.setMinRoomCapacity(minCap);
                c.setMaxRoomCapacity(maxCap);

                list.add(c);
            }
        } catch (SQLException e) {
            System.err.println("DB LOAD COURSES ERROR: " + e.getMessage());
        }
        return list;
    }

    public static void logConflict(String courseId, String reason) {
        String sql = "INSERT INTO conflict_log(course_id, reason) VALUES(?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseId);
            ps.setString(2, reason);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- Helper Records ---
    public static record SavedFileRecord(String path, String type, boolean active) {
    }

    public static record RuleRecord(int groupId, String courseId, int duration, int minCap, int maxCap) {
    }

    // Eski metodlar (Boş bırakıyoruz çünkü MainApp artık yeni mantığı kullanacak)
    public static void insertStudent(Student s) {
    }

    public static void insertClassroom(Classroom c) {
    }

    public static void insertCourse(Course c) {
    }

    public static void updateCourseRules(Course c) {
    }






    public static Map<String, List<StudentExam>> loadSchedule() {
        return new HashMap<>();
    }
    public static void saveUploadedFile(String absolutePath) {
        String sql = "INSERT OR REPLACE INTO uploaded_files(filename) VALUES(?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, absolutePath);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static List<String> loadUploadedFiles() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT filename FROM uploaded_files";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString("filename"));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

}