package scheduler;

import scheduler.core.ExamScheduler;
import scheduler.model.*;


import java.lang.Class;
import java.util.*;
import java.util.stream.Collectors;


public class Main {
    public static void main(String[] args) {
        // Placeholder data
        List<Student> students = List.of(new Student("Std_001"));
        List<Course> courses = List.of(new Course("Course_01", 90));
        List<Enrollment> enrollments = List.of(new Enrollment("Std_001", "Course_01"));
        List<Classroom> classrooms = List.of(new Classroom("Classroom_01", 50));

        ExamScheduler scheduler = new ExamScheduler();
        scheduler.run(students, courses, enrollments, classrooms);
    }
}