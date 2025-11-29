package scheduler.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DBManager {

    // SQLite veritabanı dosyasının yolu
    private static final String URL = "jdbc:sqlite:exams.db";

    /**
     * Veritabanı bağlantısını açar.
     */
    public static Connection getConnection() throws SQLException {
        // JDBC sürücüsü ile bağlantı kurma
        return DriverManager.getConnection(URL);
    }

    /**
     * Uygulamanın başlangıcında tüm tabloları oluşturur (DDL - Data Definition Language).
     */
    public static void initializeDatabase() {
        try (Connection conn = getConnection()) {
            if (conn != null) {
                System.out.println("SQLite connection successful. Initializing schema...");

                // Gerekli tüm tabloları oluştur
                createClassroomsTable(conn);
                createCoursesTable(conn);
                createStudentsTable(conn);
                createTimeSlotsTable(conn);
                createStudentEnrollmentTable(conn);
                createExamSessionsTable(conn);

                System.out.println("Database schema successfully created/verified.");
            }
        } catch (SQLException e) {
            System.err.println("CRITICAL: Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------------------------
    // TABLO OLUŞTURMA METOTLARI (CREATE TABLE)
    // ----------------------------------------------------------------------

    private static void createClassroomsTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS CLASSROOMS ("
                + " class_code TEXT PRIMARY KEY NOT NULL,"
                + " capacity INTEGER NOT NULL"
                + ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static void createCoursesTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS COURSES ("
                + " course_code TEXT PRIMARY KEY NOT NULL,"
                + " duration_minutes INTEGER NOT NULL"
                + ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static void createStudentsTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS STUDENTS ("
                + " student_id TEXT PRIMARY KEY NOT NULL"
                + ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static void createTimeSlotsTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS TIME_SLOTS ("
                + " slot_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + " date TEXT NOT NULL,"
                + " time TEXT NOT NULL,"
                + " UNIQUE(date, time)"
                + ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static void createStudentEnrollmentTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS STUDENT_ENROLLMENT ("
                + " student_id TEXT NOT NULL,"
                + " course_code TEXT NOT NULL,"
                + " PRIMARY KEY (student_id, course_code),"
                + " FOREIGN KEY (student_id) REFERENCES STUDENTS(student_id),"
                + " FOREIGN KEY (course_code) REFERENCES COURSES(course_code)"
                + ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static void createExamSessionsTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS EXAM_SESSIONS ("
                + " session_no INTEGER NOT NULL,"
                + " course_code TEXT NOT NULL,"
                + " slot_id INTEGER NOT NULL,"
                + " class_code TEXT NOT NULL,"
                + " PRIMARY KEY (course_code, session_no),"
                + " FOREIGN KEY (course_code) REFERENCES COURSES(course_code),"
                + " FOREIGN KEY (slot_id) REFERENCES TIME_SLOTS(slot_id),"
                + " FOREIGN KEY (class_code) REFERENCES CLASSROOMS(class_code)"
                + ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
