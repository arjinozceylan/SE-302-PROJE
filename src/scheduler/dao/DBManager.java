package scheduler.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import scheduler.model.Student;
import scheduler.model.Course;
import scheduler.model.*;


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





}
