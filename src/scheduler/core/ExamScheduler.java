package scheduler.core;

import scheduler.model.*;
import scheduler.config.SchedulingConfig;


import java.util.*;

public class ExamScheduler {
    public void run(List<Student> students,
                    List<Course> courses,
                    List<Enrollment> enrollments,
                    List<Classroom> classrooms) {
        System.out.println("Exam scheduler is running...");
        System.out.printf("Students=%d, Courses=%d, Enrollments=%d, Classrooms=%d%n",
                students.size(), courses.size(), enrollments.size(), classrooms.size());

        // === TEST: TimeslotBuilder ===
        System.out.println("\n--- TimeslotBuilder Test ---");

        List<DayWindow> testWindows = List.of(
                new DayWindow(
                        java.time.LocalDate.of(2025, 10, 15),
                        List.of(new TimeRange(java.time.LocalTime.of(9, 0),
                                java.time.LocalTime.of(12, 0)))
                )
        );

        TimeslotBuilder builder = new TimeslotBuilder();
        List<Timeslot> slots = builder.build(testWindows, 120);

        for (Timeslot t : slots) {
            System.out.printf("Date=%s, Start=%s, End=%s%n",
                    t.getDate(), t.getStart(), t.getEnd());
        }
        System.out.println("--- End of Test ---\n");
        // === TEST END ===
        // === CONFLICT GRAPH TEST ===
        System.out.println("--- ConflictGraph Test ---");

// Küçük demo: S1 hem C1 hem C2'de; S2 hem C2 hem C3'te → C1–C2 ve C2–C3 kenarları oluşur.
        // S1 sadece C1'de; S2 hem C2 hem C3'te; S3 C3'te
        List<Enrollment> demo = List.of(
                new Enrollment("S1", "C1"),
                new Enrollment("S2", "C2"),
                new Enrollment("S2", "C3"),
                new Enrollment("S3", "C3")
        );

        ConflictGraphBuilder cgb = new ConflictGraphBuilder();
        Map<String, Set<String>> c2s = cgb.buildCourseToStudents(demo);
        Map<String, Integer> deg = cgb.buildDegrees(c2s);

// Dereceleri yazdır
        for (var e : deg.entrySet()) {
            System.out.printf("Course=%s, degree=%d%n", e.getKey(), e.getValue());
        }

// Kenarları yazdır (aynı anda yapılamaz çiftleri)
        List<String> coursesList = new ArrayList<>(c2s.keySet());
        for (int i = 0; i < coursesList.size(); i++) {
            for (int j = i + 1; j < coursesList.size(); j++) {
                String a = coursesList.get(i), b = coursesList.get(j);
                if (!Collections.disjoint(c2s.get(a), c2s.get(b))) {
                    System.out.printf("EDGE: %s <-> %s%n", a, b);
                }
            }
        }
        System.out.println("--- End ConflictGraph Test ---\n");
// === TEST 2 END ===

        // TODO: 1. validate data
        // TODO: 2. generate timeslots
        // TODO: 3. build conflict graph
        // TODO: 4. place exams
        // TODO: 5. assign students
        // TODO: 6. output schedules
    }

}