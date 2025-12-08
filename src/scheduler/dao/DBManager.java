package scheduler.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import scheduler.model.Student;
import scheduler.model.Course;
import scheduler.model.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.time.LocalDate;
import java.time.LocalTime;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import scheduler.model.StudentExam;
import scheduler.model.Timeslot;

/**
 * Simple SQLite manager used only to initialize a DB file and create tables.
 * Your project does NOT yet save/load scheduling results into DB,
 * but this class prepares the database for future extensions.
 */
public class DBManager {

    private static final String DB_URL = "jdbc:sqlite:scheduler.db";

    /**
     * Called from MainApp.start()
     * Creates the SQLite file and required tables if missing.
     */
    public static void initializeDatabase() throws SQLException {

        try (Connection conn = DriverManager.getConnection(DB_URL)) {

            if (conn != null) {
                try (Statement st = conn.createStatement()) {

                    // STUDENTS TABLE
                    st.execute("""
                                CREATE TABLE IF NOT EXISTS students (
                                    id TEXT PRIMARY KEY
                                );
                            """);

                    // COURSES TABLE
                    st.execute("""
                                CREATE TABLE IF NOT EXISTS courses (
                                    id TEXT PRIMARY KEY,
                                    duration INTEGER
                                );
                            """);

                    // CLASSROOMS TABLE
                    st.execute("""
                                CREATE TABLE IF NOT EXISTS classrooms (
                                    id TEXT PRIMARY KEY,
                                    capacity INTEGER
                                );
                            """);

                    // ENROLLMENTS TABLE
                    st.execute("""
                                CREATE TABLE IF NOT EXISTS enrollments (
                                    student_id TEXT,
                                    course_id TEXT
                                );
                            """);

                    // SCHEDULE RESULTS (OPTIONAL)
                    st.execute("""
                                CREATE TABLE IF NOT EXISTS schedule (
                                    student_id TEXT,
                                    course_id TEXT,
                                    date TEXT,
                                    start_time TEXT,
                                    end_time TEXT,
                                    room TEXT,
                                    seat INTEGER
                                );
                            """);
                    // CONFLICT LOG (UNSCHEDULED COURSES)
                    st.execute("""
                                CREATE TABLE IF NOT EXISTS conflict_log (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                                    course_id TEXT,
                                    reason TEXT
                                );
                            """);

                }
            }
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void insertStudent(Student s) {
        String sql = "INSERT OR IGNORE INTO students(id) VALUES(?)";

        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, s.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("DB INSERT ERROR (student): " + e.getMessage());
        }
    }

    public static void insertCourse(Course c) {
        String sql = "INSERT OR REPLACE INTO courses(id, duration) VALUES(?, ?)";

        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, c.getId());
            ps.setInt(2, c.getDurationMinutes());
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("DB INSERT ERROR (course): " + e.getMessage());
        }
    }

    public static void insertClassroom(Classroom c) {
        String sql = "INSERT OR REPLACE INTO classrooms(id, capacity) VALUES(?, ?)";

        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, c.getId());
            ps.setInt(2, c.getCapacity());
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("DB INSERT ERROR (classroom): " + e.getMessage());
        }
    }

    public static void insertEnrollment(Enrollment e) {
        String sql = "INSERT INTO enrollments(student_id, course_id) VALUES(?, ?)";

        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, e.getStudentId());
            ps.setString(2, e.getCourseId());
            ps.executeUpdate();

        } catch (SQLException err) {
            System.err.println("DB INSERT ERROR (enrollment): " + err.getMessage());
        }
    }

    public static void insertSchedule(StudentExam se) {
        String sql = """
                INSERT INTO schedule(student_id, course_id, date, start_time, end_time, room, seat)
                VALUES(?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, se.getStudentId());
            ps.setString(2, se.getCourseId());
            ps.setString(3, se.getTimeslot().getDate().toString());
            ps.setString(4, se.getTimeslot().getStart().toString());
            ps.setString(5, se.getTimeslot().getEnd().toString());
            ps.setString(6, se.getClassroomId());
            ps.setInt(7, se.getSeatNo());

            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("DB INSERT ERROR (schedule): " + e.getMessage());
        }
    }

    public static void logConflict(String courseId, String reason) {
        String sql = "INSERT INTO conflict_log(course_id, reason) VALUES(?, ?)";

        try (Connection conn = getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, courseId);
            ps.setString(2, reason);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("DB INSERT ERROR (conflict_log): " + e.getMessage());
        }
    }

    public static Map<String, List<StudentExam>> loadSchedule() {
        Map<String, List<StudentExam>> map = new HashMap<>();

        String sql = "SELECT student_id, course_id, date, start_time, end_time, room, seat FROM schedule";

        try (Connection conn = DriverManager.getConnection(DB_URL);
                PreparedStatement pstmt = conn.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                String studentId = rs.getString("student_id");
                String courseId = rs.getString("course_id");
                LocalDate date = LocalDate.parse(rs.getString("date"));
                LocalTime start = LocalTime.parse(rs.getString("start_time"));
                LocalTime end = LocalTime.parse(rs.getString("end_time"));
                String classroom = rs.getString("room");
                int seat = rs.getInt("seat");

                Timeslot ts = new Timeslot(date, start, end);
                StudentExam exam = new StudentExam(studentId, courseId, ts, classroom, seat);

                map.computeIfAbsent(studentId, k -> new ArrayList<>()).add(exam);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return map;
    }
    public static void clearScheduleTable() {
        String sql = "DELETE FROM schedule";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.executeUpdate();
            System.out.println("DB CLEAR: schedule table emptied.");

        } catch (SQLException e) {
            System.err.println("DB CLEAR ERROR: " + e.getMessage());
        }
    }
    public static void clearConflictLog() {
        String sql = "DELETE FROM conflict_log";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.executeUpdate();
            System.out.println("DB CLEAR: conflict_log emptied.");

        } catch (SQLException e) {
            System.err.println("DB CLEAR ERROR: " + e.getMessage());
        }
    }


}
