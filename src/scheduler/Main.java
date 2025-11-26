package scheduler;

import scheduler.core.ExamScheduler;
import scheduler.io.CsvDataLoader;
import scheduler.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;


public class Main {
    public static void main(String[] args) {
        try {
            // Proje kökünün tam yolu:
            Path baseDir = Path.of("/Users/talatkarasakal/Library/Mobile Documents/com~apple~CloudDocs/SE-302-PROJE/src"); // kendi makinedeki tam yolu buraya yaz

            Path studentsPath    = baseDir.resolve("sampleData_AllStudents.csv");
            Path coursesPath     = baseDir.resolve("sampleData_AllCourses.csv");
            Path classroomsPath  = baseDir.resolve("sampleData_AllClassroomsAndTheirCapacities.csv");
            Path enrollmentsPath = baseDir.resolve("sampleData_AllAttendanceLists.csv");
            CsvDataLoader.debugPrintAttendance(enrollmentsPath);
            List<Enrollment> enrollments = CsvDataLoader.loadEnrollments(enrollmentsPath);
            List<Student> students       = CsvDataLoader.loadStudents(studentsPath);
            List<Course> courses         = CsvDataLoader.loadCourses(coursesPath);
            List<Classroom> classrooms   = CsvDataLoader.loadClassrooms(classroomsPath);


            ExamScheduler scheduler = new ExamScheduler();
            scheduler.run(students, courses, enrollments, classrooms);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }}