package scheduler.core;

import scheduler.assign.StudentDistributor;
import scheduler.config.SchedulingConfig;
import scheduler.constraints.*;
import scheduler.model.*;

import java.util.*;

public class ExamScheduler {

        private final Map<String, String> unscheduledReasons = new HashMap<>();

        public Map<String, String> getUnscheduledReasons() {
                return unscheduledReasons;
        }
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
                unscheduledReasons.clear();

                // 1. UI'dan gün penceresi gelmediyse dur
                if (dayWindows == null || dayWindows.isEmpty()) {
                        System.out.println("No day windows provided, skipping scheduling.");
                        return results;
                }

                // --- ODA KAPASİTE İSTATİSTİĞİ (room balancing için) ---
                int avgCapacity = 0;
                if (classrooms != null && !classrooms.isEmpty()) {
                        int sum = 0;
                        for (Classroom r : classrooms) {
                                sum += r.getCapacity();
                        }
                        avgCapacity = sum / classrooms.size();
                }

                // 2. Conflict Graph (Çakışma Grafiği) Oluştur
                ConflictGraphBuilder gb = new ConflictGraphBuilder();
                Map<String, Set<String>> courseToStudents = gb.buildCourseToStudents(enrollments);
                Map<String, Integer> degree = gb.buildDegrees(courseToStudents);

                // 3. Dersleri Sırala (En zor/kalabalık olanlar öne)
                List<Course> orderedCourses = new ArrayList<>(courses);
                Map<String, Integer> courseSize = new HashMap<>();
                courseToStudents.forEach((k, v) -> courseSize.put(k, v.size()));

                orderedCourses.sort(
                        Comparator.comparingInt((Course c) -> degree.getOrDefault(c.getId(), 0)).reversed()
                                .thenComparingInt(c -> courseSize.getOrDefault(c.getId(), 0)).reversed()
                );

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
                        // Öğrenci çakışması + minimum aralık
                        .add(new NoStudentClashAndMinGap(
                                courseToStudents,
                                SchedulingConfig.MIN_GAP_MINUTES
                        ))
                        // Aynı öğrencinin bir günde max sınav sayısı
                        .add(new MaxExamsPerDay(
                                courseToStudents,
                                SchedulingConfig.MAX_EXAMS_PER_DAY
                        ));

                PartialSchedule schedule = new PartialSchedule();

                for (Course c : orderedCourses) {
                        int need = courseSize.getOrDefault(c.getId(), 0);
                        if (need == 0) continue;

                        // --- ROOM BALANCING KARARI ---
                        // Dersin büyüklüğü ortalama sınıf kapasitesinden büyükse → büyük odalar,
                        // küçükse → küçük odalar tercih edilsin.
                        boolean preferLargeRoomsFirst =
                                (avgCapacity == 0) || (need >= avgCapacity);

                        // 1. Greedy oda seçimi
                        List<Classroom> pickedGreedy =
                                rcg.generateGreedyOrdered(classrooms, need, preferLargeRoomsFirst);

                        List<List<Classroom>> roomCandidates = new ArrayList<>();

                        if (RoomComboGenerator.totalCapacity(pickedGreedy) >= need) {
                                roomCandidates.add(pickedGreedy);
                        }

                        // 2. Daha verimli kombinasyonlar (tekli / ikili / üçlü)
                        roomCandidates.addAll(
                                rcg.generateMinimalCombos(classrooms, need, 50)
                        );

                        if (roomCandidates.isEmpty()) {
                                String msg = "Not enough total classroom capacity for " + need + " students.";
                                unscheduledReasons.put(c.getId(), msg);
                                System.err.println("UNSCHEDULED COURSE: " + c.getId() + " (" + msg + ")");
                                continue;
                        }

                        // --- ZAMAN + ODA ARAMASI ---
                        // --- ZAMAN + ODA ARAMASI ---
                        boolean placed = false;
                        String lastFailReason = null;

                        List<Timeslot> slots = slotsPerCourse.get(c.getId());

                        if (slots != null) {
                                for (List<Classroom> roomSet : roomCandidates) {
                                        for (Timeslot t : slots) {
                                                Candidate cand = new Candidate(c.getId(), t, roomSet);

                                                if (constraints.ok(schedule, cand)) {
                                                        // Bu kombinasyon tüm constraintleri sağlıyor → yerleştir
                                                        schedule.addPlacement(new Placement(c.getId(), t, roomSet));
                                                        placed = true;
                                                        break;
                                                }
                                        }
                                        if (placed) break;
                                }
                        }

                // ---------- MINI BACKTRACKING START ----------
                if (!placed) {
                        // 1) Son eklenen 3 placement'ı al
                        List<String> lastCourses = new ArrayList<>(schedule.getPlacements().keySet())
                                .subList(Math.max(0, schedule.getPlacements().size() - 3),
                                        schedule.getPlacements().size());

                        // 2) Onları geri al (geri sar)
                        List<Placement> removed = new ArrayList<>();
                        for (String lastC : lastCourses) {
                                removed.add(schedule.removePlacement(lastC));
                        }

                        // 3) Bu dersi tekrar dene (başka room/time kombinasyonlarıyla)
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

                        // 4) Eğer hala yerleşmemişse geri aldıklarımı da geri koy
                        if (!placed) {
                                for (Placement p : removed) {
                                        if (p != null) schedule.addPlacement(p);
                                }
                        }
                }
// ---------- MINI BACKTRACKING END ----------
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
                                SchedulingConfig.RANDOM_SEED
                        );

                        // Sonuçları Öğrenci ID'sine göre grupla (UI için)
                        for (StudentExam se : assignments) {
                                results.computeIfAbsent(se.getStudentId(), k -> new ArrayList<>()).add(se);
                        }
                }

                System.out.println("Scheduler finished. Assigned exams for " + results.size() + " students.");
                return results;
        }
}