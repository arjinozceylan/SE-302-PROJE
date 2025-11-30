package scheduler.core;

import scheduler.assign.StudentDistributor;
import scheduler.config.SchedulingConfig;
import scheduler.constraints.*;
import scheduler.model.*;

import java.util.*;

public class ExamScheduler {

        /**
         * Executes the scheduling algorithm and returns the assignments.
         */
        public Map<String, List<StudentExam>> run(List<Student> students,
                                                  List<Course> courses,
                                                  List<Enrollment> enrollments,
                                                  List<Classroom> classrooms,
                                                  List<DayWindow> dayWindows) {

                System.out.println("Scheduler started...");
                Map<String, List<StudentExam>> results = new HashMap<>();

                // 1. UI'dan gün penceresi gelmediyse dur
                if (dayWindows == null || dayWindows.isEmpty()) {
                        System.out.println("No day windows provided, skipping scheduling.");
                        return results;
                }

                // 2. Conflict Graph (Çakışma Grafiği) Oluştur
                ConflictGraphBuilder gb = new ConflictGraphBuilder();
                Map<String, Set<String>> courseToStudents = gb.buildCourseToStudents(enrollments);
                Map<String, Integer> degree = gb.buildDegrees(courseToStudents);

                // 3. Dersleri Sırala (En zor/kalabalık olanlar öne)
                List<Course> orderedCourses = new ArrayList<>(courses);
                Map<String, Integer> courseSize = new HashMap<>();
                courseToStudents.forEach((k, v) -> courseSize.put(k, v.size()));

                orderedCourses.sort(Comparator.comparingInt((Course c) -> degree.getOrDefault(c.getId(), 0)).reversed()
                        .thenComparingInt(c -> courseSize.getOrDefault(c.getId(), 0)).reversed());

                // 4. Dersler için Timeslot (Zaman Dilimi) Üret
                TimeslotBuilder tsb = new TimeslotBuilder();
                Map<String, List<Timeslot>> slotsPerCourse = new HashMap<>();
                for (Course c : orderedCourses) {
                        slotsPerCourse.put(c.getId(), tsb.build(dayWindows, c.getDurationMinutes()));
                }

                // 5. Yerleştirme Mantığı (Placement Logic)
                RoomComboGenerator rcg = new RoomComboGenerator();

                ConstraintSet constraints = new ConstraintSet()
                        .add(new OneExamPerRoomPerTime())
                        // Öğrenci çakışması + minimum aralık (örn: 45 dk)
                        .add(new NoStudentClashAndMinGap(
                                courseToStudents,
                                SchedulingConfig.MIN_GAP_MINUTES
                        ))
                        // --- DÜZELTME BURADA YAPILDI ---
                        // StudentDailyLimit yerine MaxExamsPerDay kullanıldı
                        .add(new MaxExamsPerDay(
                                courseToStudents,
                                SchedulingConfig.MAX_EXAMS_PER_DAY
                        ));

                PartialSchedule schedule = new PartialSchedule();

                for (Course c : orderedCourses) {
                        int need = courseSize.getOrDefault(c.getId(), 0);
                        if (need == 0) continue;

                        // --- ODA SEÇİM MANTIĞI ---
                        // 1. Önce Greedy (En hızlı yol) dene
                        List<Classroom> pickedGreedy = rcg.generateGreedyOrdered(classrooms, need, true);
                        List<List<Classroom>> roomCandidates = new ArrayList<>();

                        if (RoomComboGenerator.totalCapacity(pickedGreedy) >= need) {
                                roomCandidates.add(pickedGreedy);
                        }

                        // 2. Ayrıca minimal kombinasyonları da ekle (Daha verimli doluluk için)
                        roomCandidates.addAll(
                                rcg.generateMinimalCombos(classrooms, need, 50)
                        );

                        if (roomCandidates.isEmpty()) {
                                System.err.println("NO ROOM COMBINATIONS for course " + c.getId());
                                continue;
                        }

                        // --- ZAMAN + ODA ARAMASI ---
                        boolean placed = false;
                        List<Timeslot> slots = slotsPerCourse.get(c.getId());

                        if (slots != null) {
                                // Kombinasyonları sırayla dene
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

                // 6. Öğrencileri Koltuklara Ata (StudentDistributor)
                StudentDistributor distributor = new StudentDistributor();

                for (Placement p : schedule.getPlacements().values()) {
                        Set<String> studentIds = courseToStudents.get(p.getCourseId());
                        if (studentIds == null) continue;

                        List<StudentExam> assignments = distributor.assign(
                                p.getCourseId(),
                                p.getTimeslot(),
                                p.getClassrooms(),
                                new ArrayList<>(studentIds),
                                SchedulingConfig.RANDOM_SEED);

                        // Sonuçları Öğrenci ID'sine göre grupla (UI için)
                        for (StudentExam se : assignments) {
                                results.computeIfAbsent(se.getStudentId(), k -> new ArrayList<>()).add(se);
                        }
                }

                System.out.println("Scheduler finished. Assigned exams for " + results.size() + " students.");
                return results;
        }
}