package scheduler;

import scheduler.core.ExamScheduler;
import scheduler.model.*;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        // === STUDENT LIST ===
// Math153 → 300 öğrenci
// Math154 → 500 öğrenci
        List<Student> students = new ArrayList<>();

// Math153 öğrencileri: S001–S300
        List<String> math153Students = new ArrayList<>();
        for (int i = 1; i <= 300; i++) {
            String id = String.format("S%03d", i);
            students.add(new Student(id));
            math153Students.add(id);
        }

// Math154 öğrencileri: S301–S800 (500 öğrenci)
        List<String> math154Students = new ArrayList<>();
        for (int i = 301; i <= 800; i++) {
            String id = String.format("S%03d", i);
            students.add(new Student(id));
            math154Students.add(id);
        }
        // === COURSES ===


// === ENROLLMENTS ===
        List<Enrollment> enrollments = new ArrayList<>();

// Sadece Math153'e kayıtlı 300 kişi
        for (String id : math153Students) {
            enrollments.add(new Enrollment(id, "Math153"));
        }

// Sadece Math154'e kayıtlı 500 kişi
        for (String id : math154Students) {
            enrollments.add(new Enrollment(id, "Math154"));
        }
        List<Course> courses = List.of(
                new Course("Math153", 90),   // 90 dakika
                new Course("Math154", 90)
        );
        // 4) Farklı kapasiteli sınıflar
        List<Classroom> classrooms = List.of(
                new Classroom("M101", 50),
                new Classroom("M102", 120),
                new Classroom("M103", 150),
                new Classroom("M104", 80) ,
                new Classroom("M201", 50),
                new Classroom("M202", 125) ,
                new Classroom("M203",190)
        );

        // 5) Çalıştır
        ExamScheduler scheduler = new ExamScheduler();
        scheduler.run(students, courses, enrollments, classrooms);
    }
}