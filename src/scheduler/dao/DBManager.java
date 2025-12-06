package scheduler.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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
}
