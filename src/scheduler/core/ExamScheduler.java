package scheduler.core;

import scheduler.assign.StudentDistributor;
import scheduler.config.SchedulingConfig;
import scheduler.constraints.*;
import scheduler.model.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class ExamScheduler {

        /**
         * Executes the scheduling algorithm and returns the assignments.
         * * @param students List of all students
         * 
         * @param courses     List of all courses
         * @param enrollments List of all enrollments
         * @param classrooms  List of all classrooms
         * @return A Map where Key = StudentID, Value = List of their scheduled exams
         */
        public Map<String, List<StudentExam>> run(List<Student> students,
                        List<Course> courses,
                        List<Enrollment> enrollments,
                        List<Classroom> classrooms) {

                System.out.println("Scheduler started...");
                Map<String, List<StudentExam>> results = new HashMap<>();

                // 1. Preparation: Define Time Windows (Example: 2 Days)
                // In a real scenario, this could be passed as a parameter or loaded from a
                // file.
                List<DayWindow> dayWindows = List.of(
                                new DayWindow(LocalDate.of(2025, 11, 20),
                                                List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0)))),
                                new DayWindow(LocalDate.of(2025, 11, 21),
                                                List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0)))));

                // 2. Build Conflict Graph
                ConflictGraphBuilder gb = new ConflictGraphBuilder();
                Map<String, Set<String>> courseToStudents = gb.buildCourseToStudents(enrollments);
                Map<String, Integer> degree = gb.buildDegrees(courseToStudents);

                // 3. Sort Courses (Hardest/Largest first)
                List<Course> orderedCourses = new ArrayList<>(courses);
                Map<String, Integer> courseSize = new HashMap<>();
                courseToStudents.forEach((k, v) -> courseSize.put(k, v.size()));

                orderedCourses.sort(Comparator.comparingInt((Course c) -> degree.getOrDefault(c.getId(), 0)).reversed()
                                .thenComparingInt(c -> courseSize.getOrDefault(c.getId(), 0)).reversed());

                // 4. Generate Timeslots for each course
                TimeslotBuilder tsb = new TimeslotBuilder();
                Map<String, List<Timeslot>> slotsPerCourse = new HashMap<>();
                for (Course c : orderedCourses) {
                        slotsPerCourse.put(c.getId(), tsb.build(dayWindows, c.getDurationMinutes()));
                }

                // 5. Placement Logic (Greedy)
                RoomComboGenerator rcg = new RoomComboGenerator();
                ConstraintSet constraints = new ConstraintSet()
                                .add(new OneExamPerRoomPerTime())
                                .add(new NoStudentClashAndMinGap(courseToStudents, 30)); // 30 min gap constraint

                PartialSchedule schedule = new PartialSchedule();

                for (Course c : orderedCourses) {
                        int need = courseSize.getOrDefault(c.getId(), 0);
                        if (need == 0)
                                continue;

                        // Pick Rooms
                        List<Classroom> picked = rcg.generateGreedyOrdered(classrooms, need, true);
                        if (RoomComboGenerator.totalCapacity(picked) < need) {
                                System.err.println("Warning: Not enough capacity for " + c.getId());
                                continue;
                        }

                        // Find valid timeslot
                        List<Timeslot> slots = slotsPerCourse.get(c.getId());
                        if (slots != null) {
                                for (Timeslot t : slots) {
                                        Candidate cand = new Candidate(c.getId(), t, picked);
                                        if (constraints.ok(schedule, cand)) {
                                                schedule.addPlacement(new Placement(c.getId(), t, picked));
                                                break;
                                        }
                                }
                        }
                }

                // 6. Assign Students to Seats (using your StudentDistributor)
                StudentDistributor distributor = new StudentDistributor();

                for (Placement p : schedule.getPlacements().values()) {
                        Set<String> studentIds = courseToStudents.get(p.getCourseId());
                        if (studentIds == null)
                                continue;

                        List<StudentExam> assignments = distributor.assign(
                                        p.getCourseId(),
                                        p.getTimeslot(),
                                        p.getClassrooms(),
                                        new ArrayList<>(studentIds),
                                        SchedulingConfig.RANDOM_SEED);

                        // Group results by Student ID for the UI
                        for (StudentExam se : assignments) {
                                results.computeIfAbsent(se.getStudentId(), k -> new ArrayList<>()).add(se);
                        }
                }

                System.out.println("Scheduler finished. Assigned exams for " + results.size() + " students.");
                return results;
        }
}