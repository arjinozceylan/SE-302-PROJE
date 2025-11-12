package scheduler;

import scheduler.core.ExamScheduler;
import scheduler.model.*;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // 1) 40 öğrenci üret
        List<Student> students = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            students.add(new Student(String.format("Std_%03d", i)));
        }

        // 2) Tek ders: Course_01 (90 dk)
        List<Course> courses = List.of(new Course("Course_01", 90));

        // 3) Tüm öğrencileri Course_01'e kaydet
        List<Enrollment> enrollments = new ArrayList<>();
        for (Student s : students) {
            enrollments.add(new Enrollment(s.getId(), "Course_01"));
        }

        // 4) Farklı kapasiteli sınıflar
        List<Classroom> classrooms = List.of(
                new Classroom("Classroom_01", 50),
                new Classroom("Classroom_02", 120),
                new Classroom("Classroom_03", 150),
                new Classroom("Classroom_04", 80)
        );

        // 5) Çalıştır
        ExamScheduler scheduler = new ExamScheduler();
        scheduler.run(students, courses, enrollments, classrooms);
    }
}