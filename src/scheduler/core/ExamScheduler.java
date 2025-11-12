package scheduler.core;

import scheduler.model.*;
import scheduler.config.SchedulingConfig;


import java.util.List;

public class ExamScheduler {
    public void run(List<Student> students,
                    List<Course> courses,
                    List<Enrollment> enrollments,
                    List<Classroom> classrooms) {
        System.out.println("Exam scheduler is running...");
        System.out.printf("Students=%d, Courses=%d, Enrollments=%d, Classrooms=%d%n",
                students.size(), courses.size(), enrollments.size(), classrooms.size());
        // TODO: 1. validate data
        // TODO: 2. generate timeslots
        // TODO: 3. build conflict graph
        // TODO: 4. place exams
        // TODO: 5. assign students
        // TODO: 6. output schedules
    }
}