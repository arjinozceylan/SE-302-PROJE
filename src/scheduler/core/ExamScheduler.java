package scheduler.core;

import scheduler.assign.StudentDistributor;
import scheduler.config.SchedulingConfig;
import scheduler.constraints.*;
import scheduler.model.*;
import scheduler.config.SchedulingConfig;
import scheduler.constraints.MaxExamsPerDay;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import scheduler.constraints.StudentDailyLimit;
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
                                                  List<Classroom> classrooms,
                                                  List<DayWindow> dayWindows) {

                System.out.println("Scheduler started...");
                Map<String, List<StudentExam>> results = new HashMap<>();

                // 1. Preparation: Define Time Windows (Example: 2 Days)
                // In a real scenario, this could be passed as a parameter or loaded from a
                // file.
                // Eğer UI bir pencere göndermediyse, planlama yapma
                if (dayWindows == null || dayWindows.isEmpty()) {
                        System.out.println("No day windows provided, skipping scheduling.");
                        return results;
                }
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
                        // Öğrenci çakışması + minimum 120 dakika aralık
                        .add(new NoStudentClashAndMinGap(
                                courseToStudents,
                                SchedulingConfig.MIN_GAP_MINUTES
                        ))
                        // Aynı öğrenci için bir günde en fazla 2 sınav
                        .add(new StudentDailyLimit(
                                courseToStudents,
                                SchedulingConfig.MAX_EXAMS_PER_DAY
                        ));
                PartialSchedule schedule = new PartialSchedule();

                for (Course c : orderedCourses) {
                        int need = courseSize.getOrDefault(c.getId(), 0);
                        if (need == 0)
                                continue;

                        // Pick Rooms
                        // ==== NEW ROOM PICKING LOGIC ====
// First try greedy (fastest option)
                        List<Classroom> pickedGreedy = rcg.generateGreedyOrdered(classrooms, need, true);

                        List<List<Classroom>> roomCandidates = new ArrayList<>();

// 1) Greedy çözüm kapasiteyi karşılarsa ilk sıraya ekle
                        if (RoomComboGenerator.totalCapacity(pickedGreedy) >= need) {
                                roomCandidates.add(pickedGreedy);
                        }

// 2) Ayrıca minimal kombinasyonları da ekle (çok önemli!)
                        roomCandidates.addAll(
                                rcg.generateMinimalCombos(classrooms, need, 50)  // max 50 farklı kombinasyon
                        );

// Hiç kombinasyon yoksa schedule edilemez
                        if (roomCandidates.isEmpty()) {
                                System.err.println("NO ROOM COMBINATIONS for course " + c.getId());
                                continue;
                        }

// ==== TIME + ROOM search ====
// Tüm roomCandidate kombinasyonlarını sırayla dene
                        boolean placed = false;

                        List<Timeslot> slots = slotsPerCourse.get(c.getId());
                        if (slots != null) {
                                // kombinasyonları sırayla dene
                                for (List<Classroom> roomSet : roomCandidates) {
                                        for (Timeslot t : slots) {

                                                Candidate cand = new Candidate(c.getId(), t, roomSet);

                                                if (constraints.ok(schedule, cand)) {
                                                        schedule.addPlacement(new Placement(c.getId(), t, roomSet));
                                                        placed = true;
                                                        break;
                                                }
                                        }
                                        if (placed) break;
                                }
                        }

                        if (!placed) {
                                System.err.println("UNSCHEDULED COURSE: " + c.getId());
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