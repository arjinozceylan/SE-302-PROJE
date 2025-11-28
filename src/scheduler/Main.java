package scheduler;

import scheduler.core.ExamScheduler;
import scheduler.io.CsvDataLoader;
import scheduler.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import scheduler.model.DayWindow;
import scheduler.model.TimeRange;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;


public class Main {
    public static void main(String[] args) {
        try {
            // Proje kökünün tam yolu:
            Path baseDir = Path.of("/Users/arjinozceylan/IdeaProjects/SE 302 PROJE/src"); // kendi makinedeki tam yolu buraya yaz

            Path studentsPath    = baseDir.resolve("sampleData_AllStudents.csv");
            Path coursesPath     = baseDir.resolve("sampleData_AllCourses.csv");
            Path classroomsPath  = baseDir.resolve("sampleData_AllClassroomsAndTheirCapacities.csv");
            Path enrollmentsPath = baseDir.resolve("sampleData_AllAttendanceLists.csv");
            CsvDataLoader.debugPrintAttendance(enrollmentsPath);
            List<Enrollment> enrollments = CsvDataLoader.loadEnrollments(enrollmentsPath);
            List<Student> students       = CsvDataLoader.loadStudents(studentsPath);
            List<Course> courses         = CsvDataLoader.loadCourses(coursesPath);
            List<Classroom> classrooms   = CsvDataLoader.loadClassrooms(classroomsPath);


            // Örnek olarak 20–21 Kasım 09:00–17:00 aralığını kullanıyoruz
            List<DayWindow> dayWindows = List.of(
                    new DayWindow(LocalDate.of(2025, 11, 20),
                            List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0)))),
                    new DayWindow(LocalDate.of(2025, 11, 21),
                            List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))))
            );

            ExamScheduler scheduler = new ExamScheduler();
            scheduler.run(students, courses, enrollments, classrooms, dayWindows);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }}