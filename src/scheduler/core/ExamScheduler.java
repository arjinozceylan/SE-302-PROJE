package scheduler.core;

import scheduler.assign.StudentDistributor;
import scheduler.config.SchedulingConfig;
import scheduler.constraints.*;
import scheduler.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class ExamScheduler {

        private final Map<String, String> unscheduledReasons = new HashMap<>();
        // Track room usage across the whole run (for balancing)
        private final Map<String, Integer> roomUseCount = new HashMap<>();

        public Map<String, String> getUnscheduledReasons() {
                return unscheduledReasons;
        }

        public Map<String, List<StudentExam>> run(List<Student> students,
                        List<Course> courses,
                        List<Enrollment> enrollments,
                        List<Classroom> classrooms,
                        List<DayWindow> dayWindows) {

                System.out.println("Scheduler started...");
                unscheduledReasons.clear();
                roomUseCount.clear();
                Map<String, List<StudentExam>> results = new HashMap<>();

                if (dayWindows == null || dayWindows.isEmpty()) {
                        System.out.println("No day windows provided.");
                        return results;
                }

                // 1. Hazırlık: Grafikler, İstatistikler ve Timeslotlar
                ConflictGraphBuilder gb = new ConflictGraphBuilder();
                Map<String, Set<String>> courseToStudents = gb.buildCourseToStudents(enrollments);
                Map<String, Integer> degrees = gb.buildDegrees(courseToStudents);

                // Timeslot'ları ÖNCE oluştur (ders esnekliği için)
                TimeslotBuilder tsb = new TimeslotBuilder();
                Map<String, List<Timeslot>> slotsPerCourse = new HashMap<>();
                for (Course c : courses) {
                        slotsPerCourse.put(c.getId(), tsb.build(dayWindows, c.getDurationMinutes()));
                }

                // Dersleri GERÇEK zorluk derecesine göre sırala (least flexibility first)
                List<Course> orderedCourses = sortCourses(courses, courseToStudents, degrees, slotsPerCourse);

                // Kısıtları (Constraints) Hazırla
                ConstraintSet constraints = new ConstraintSet()
                                .add(new OneExamPerRoomPerTime())
                                .add(new NoStudentClashAndMinGap(courseToStudents, SchedulingConfig.MIN_GAP_MINUTES))
                                .add(new MaxExamsPerDay(courseToStudents, SchedulingConfig.MAX_EXAMS_PER_DAY));

                // Yerleştirme Motoru
                PartialSchedule schedule = new PartialSchedule();
                RoomComboGenerator rcg = new RoomComboGenerator();

                // --- ANA DÖNGÜ ---
                for (Course c : orderedCourses) {
                        int studentCount = courseToStudents.getOrDefault(c.getId(), Collections.emptySet()).size();

                        if (studentCount == 0) {
                                logError(c.getId(), "No enrollments found (0 students).");
                                continue;
                        }

                        // Olası Oda Kombinasyonlarını Bul
                        List<List<Classroom>> roomCandidates = findRoomCandidates(c, classrooms, studentCount, rcg);
                        if (roomCandidates.isEmpty())
                                continue; // Hata logu metodun içinde yazıldı

                        List<Timeslot> slots = slotsPerCourse.get(c.getId());

                        // 1. ADIM: Normal Yerleştirme Dene
                        if (attemptPlace(c, schedule, slots, roomCandidates, constraints)) {
                                continue; // Başarılı
                        }

                        // 2. ADIM: Transactional Backtracking (Son çare)
                        if (tryBacktracking(c, schedule, slots, roomCandidates, slotsPerCourse, constraints)) {
                                continue; // Başarılı
                        }

                        // 3. ADIM: Hata Analizi (Neden olmadı?)
                        analyzeFailure(c, schedule, slots, roomCandidates, constraints, courseToStudents);
                }

                // Fallback: Gözden kaçanlar
                markUnknownFailures(courses, schedule);

                // Sonuçları Veritabanına Yaz ve Döndür
                return finalizeSchedule(schedule, courseToStudents, results);
        }

        // --- YARDIMCI METODLAR (Private Helpers) ---

        private List<Course> sortCourses(List<Course> courses,
                        Map<String, Set<String>> c2s,
                        Map<String, Integer> degrees,
                        Map<String, List<Timeslot>> slotsPerCourse) {
                List<Course> sorted = new ArrayList<>(courses);
                sorted.sort(Comparator
                                // 1) En az timeslotu olan ders (least flexibility)
                                .comparingInt((Course c) -> slotsPerCourse
                                                .getOrDefault(c.getId(), Collections.emptyList()).size())
                                // 2) Conflict degree (yüksek = zor)
                                .thenComparingInt(c -> degrees.getOrDefault(c.getId(), 0)).reversed()
                                // 3) Öğrenci sayısı (yüksek = zor)
                                .thenComparingInt(c -> c2s.getOrDefault(c.getId(), Collections.emptySet()).size())
                                .reversed()
                                // Stabilite için
                                .thenComparing(Course::getId));
                return sorted;
        }

        private List<List<Classroom>> findRoomCandidates(Course c, List<Classroom> classrooms, int needed,
                        RoomComboGenerator rcg) {
                // Kapasite Filtreleri
                List<Classroom> filtered = classrooms.stream()
                                .filter(r -> (c.getMinRoomCapacity() <= 0 || r.getCapacity() >= c.getMinRoomCapacity()))
                                .filter(r -> (c.getMaxRoomCapacity() <= 0 || r.getCapacity() <= c.getMaxRoomCapacity()))
                                .collect(Collectors.toList());

                if (filtered.isEmpty()) {
                        logError(c.getId(), "Configuration Error: No rooms match capacity filters.");
                        return Collections.emptyList();
                }

                // Kombinasyon Üretimi
                List<Classroom> greedy = rcg.generateGreedyOrdered(filtered, needed, true);
                List<List<Classroom>> candidates = new ArrayList<>();

                if (RoomComboGenerator.totalCapacity(greedy) >= needed) {
                        candidates.add(greedy);
                }
                candidates.addAll(rcg.generateMinimalCombos(filtered, needed, 50));

                if (candidates.isEmpty()) {
                        logError(c.getId(),
                                "Infrastructure Error: Insufficient total room capacity (needed="
                                + needed + ", rooms=" + filtered.size() + ")");
                }

                // --- Balance room usage (deterministic) ---

                Random rnd = new Random(42L ^ (c.getId() == null ? 0 : c.getId().hashCode()));
                Collections.shuffle(candidates, rnd);
                candidates.sort(Comparator
                                .comparingInt((List<Classroom> rs) -> rs.stream()
                                                .mapToInt(r -> roomUseCount.getOrDefault(r.getId(), 0)).sum())
                                .thenComparingInt(rs -> Math.max(0,
                                                RoomComboGenerator.totalCapacity(rs) - needed))
                                .thenComparingInt(rs -> rs.stream()
                                                .mapToInt(Classroom::getCapacity).max().orElse(0)));

                return candidates;
        }

        private boolean attemptPlace(Course c, PartialSchedule schedule, List<Timeslot> slots,
                        List<List<Classroom>> candidates, ConstraintSet constraints) {
                if (slots == null || candidates == null)
                        return false;
                int retries = Math.min(2, candidates.size());
                for (int i = 0; i < retries; i++) {
                        List<Classroom> rooms = candidates.get(i);
                        for (Timeslot t : slots) {
                                Candidate cand = new Candidate(c.getId(), t, rooms);
                                if (constraints.ok(schedule, cand)) {
                                        schedule.addPlacement(new Placement(c.getId(), t, rooms));
                                        // update room usage counts
                                        for (Classroom r : rooms) {
                                                roomUseCount.put(r.getId(), roomUseCount.getOrDefault(r.getId(), 0) + 1);
                                        }
                                        return true;
                                }
                        }
                }
                return false;
        }

        private boolean tryBacktracking(Course c, PartialSchedule schedule, List<Timeslot> cSlots,
                        List<List<Classroom>> cCandidates, Map<String, List<Timeslot>> allSlots,
                        ConstraintSet constraints) {
                // Son eklenen 3 dersi "Kurban" olarak seç
                List<String> currentIds = new ArrayList<>(schedule.getPlacements().keySet());
                if (currentIds.isEmpty())
                        return false;

                int removeCount = Math.min(3, currentIds.size());
                List<String> victims = currentIds.subList(currentIds.size() - removeCount, currentIds.size());
                List<Placement> originalPlacements = new ArrayList<>();

                // Kurbanları geçici olarak çıkar
                for (String vid : victims)
                        originalPlacements.add(schedule.removePlacement(vid));

                // Zor dersi yerleştirmeyi dene
                boolean mainPlaced = attemptPlace(c, schedule, cSlots, cCandidates, constraints);
                boolean allRestored = true;

                if (mainPlaced) {
                        for (Placement p : originalPlacements) {
                                List<List<Classroom>> singleCandidateList = List.of(p.getClassrooms());
                                if (attemptPlace(new Course(p.getCourseId(), 0), schedule,
                                                allSlots.get(p.getCourseId()), singleCandidateList, constraints)) {
                                } else {
                                        allRestored = false;
                                        break;
                                }
                        }
                }

                // Hepsi yerleşti mi?
                if (mainPlaced && allRestored) {
                        return true; // İşlem başarılı
                } else {
                        // Her şeyi geri al
                        if (mainPlaced)
                                schedule.removePlacement(c.getId());

                        // Şu an schedule'da olan ve victims listesinde olanları sil
                        for (String vid : victims)
                                schedule.removePlacement(vid);

                        // Orijinalleri geri yükle
                        for (Placement p : originalPlacements)
                                schedule.addPlacement(p);
                        return false;
                }
        }

        private void analyzeFailure(Course c, PartialSchedule schedule, List<Timeslot> slots,
                        List<List<Classroom>> candidates, ConstraintSet constraints,
                        Map<String, Set<String>> courseToStudents) {
                Map<String, Integer> reasons = new HashMap<>();
                if (slots != null) {
                        for (List<Classroom> rooms : candidates) {
                                for (Timeslot t : slots) {
                                        constraints.explain(schedule, new Candidate(c.getId(), t, rooms))
                                                        .forEach(r -> reasons.put(r, reasons.getOrDefault(r, 0) + 1));
                                }
                        }
                }

                String msg = reasons.isEmpty() ? "Configuration Error: No valid timeslots."
                                : "Constraint Error: " + reasons.entrySet().stream().max(Map.Entry.comparingByValue())
                                                .get().getKey();

                String bottlenecks = formatBottleneckStudents(c.getId(), schedule, courseToStudents);
                if (!bottlenecks.isEmpty()) {
                        msg = msg + " | Bottleneck students: " + bottlenecks;
                }

                logError(c.getId(), msg);
        }

        private static final int BOTTLENECK_STUDENT_LIMIT = 10;

        private String formatBottleneckStudents(String courseId, PartialSchedule schedule,
                        Map<String, Set<String>> courseToStudents) {
                if (courseId == null || schedule == null || courseToStudents == null)
                        return "";

                Set<String> studentsInCourse = courseToStudents.getOrDefault(courseId, Collections.emptySet());
                if (studentsInCourse.isEmpty())
                        return "";

                Map<String, Integer> load = computeStudentExamLoad(schedule, courseToStudents);

                return studentsInCourse.stream()
                                .sorted((a, b) -> Integer.compare(load.getOrDefault(b, 0), load.getOrDefault(a, 0)))
                                .limit(BOTTLENECK_STUDENT_LIMIT)
                                .map(sid -> sid + "(" + load.getOrDefault(sid, 0) + ")")
                                .collect(Collectors.joining(", "));
        }

        private Map<String, Integer> computeStudentExamLoad(PartialSchedule schedule,
                        Map<String, Set<String>> courseToStudents) {
                Map<String, Integer> load = new HashMap<>();
                for (Placement p : schedule.getPlacements().values()) {
                        Set<String> ss = courseToStudents.getOrDefault(p.getCourseId(), Collections.emptySet());
                        for (String sid : ss) {
                                load.put(sid, load.getOrDefault(sid, 0) + 1);
                        }
                }
                return load;
        }

        private void markUnknownFailures(List<Course> courses, PartialSchedule schedule) {
                for (Course c : courses) {
                        if (!schedule.contains(c.getId()) && !unscheduledReasons.containsKey(c.getId())) {
                                unscheduledReasons.put(c.getId(), "Skipped (Unknown Reason)");
                        }
                }
        }

        private Map<String, List<StudentExam>> finalizeSchedule(PartialSchedule schedule,
                        Map<String, Set<String>> courseToStudents,
                        Map<String, List<StudentExam>> results) {

                // Defensive defaults
                if (results == null) {
                        results = new HashMap<>();
                }
                if (schedule == null || courseToStudents == null) {
                        return results;
                }

                StudentDistributor distributor = new StudentDistributor();

                // Write placements to DB and build per-student result map
                for (Placement p : schedule.getPlacements().values()) {
                        String courseId = p.getCourseId();
                        Timeslot timeslot = p.getTimeslot();
                        List<Classroom> rooms = p.getClassrooms();

                        Set<String> studentIds = courseToStudents.getOrDefault(courseId, Collections.emptySet());
                        if (studentIds.isEmpty()) {
                                continue;
                        }

                        // Assign students deterministically to rooms
                        List<StudentExam> assignments = distributor.assign(
                                        courseId,
                                        timeslot,
                                        rooms,
                                        new ArrayList<>(studentIds),
                                        SchedulingConfig.RANDOM_SEED);

                        for (StudentExam se : assignments) {
                                results.computeIfAbsent(se.getStudentId(), k -> new ArrayList<>()).add(se);
                        }
                }

                return results;
        }

        private void logError(String courseId, String msg) {
                unscheduledReasons.put(courseId, msg);
        }
}