package scheduler.core;

import scheduler.assign.StudentDistributor;
import scheduler.config.SchedulingConfig;
import scheduler.constraints.*;
import scheduler.model.*;
import scheduler.dao.DBManager;

import java.util.*;
import java.util.stream.Collectors;

public class ExamScheduler {

        private final Map<String, String> unscheduledReasons = new HashMap<>();

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
                Map<String, List<StudentExam>> results = new HashMap<>();

                if (dayWindows == null || dayWindows.isEmpty()) {
                        System.out.println("No day windows provided.");
                        return results;
                }

                // 1. Hazırlık: Grafikler, İstatistikler ve Timeslotlar
                ConflictGraphBuilder gb = new ConflictGraphBuilder();
                Map<String, Set<String>> courseToStudents = gb.buildCourseToStudents(enrollments);
                Map<String, Integer> degrees = gb.buildDegrees(courseToStudents);

                // Dersleri "Zorluk Derecesine" göre sırala
                List<Course> orderedCourses = sortCourses(courses, courseToStudents, degrees);

                // Timeslot'ları oluştur
                TimeslotBuilder tsb = new TimeslotBuilder();
                Map<String, List<Timeslot>> slotsPerCourse = new HashMap<>();
                for (Course c : orderedCourses) {
                        slotsPerCourse.put(c.getId(), tsb.build(dayWindows, c.getDurationMinutes()));
                }

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
                        if (roomCandidates.isEmpty()) continue; // Hata logu metodun içinde yazıldı

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
                        analyzeFailure(c, schedule, slots, roomCandidates, constraints);
                }

                // Fallback: Gözden kaçanlar
                markUnknownFailures(courses, schedule);

                // Sonuçları Veritabanına Yaz ve Döndür
                return finalizeSchedule(schedule, courseToStudents, results);
        }

        // --- YARDIMCI METODLAR (Private Helpers) ---

        private List<Course> sortCourses(List<Course> courses, Map<String, Set<String>> c2s, Map<String, Integer> degrees) {
                List<Course> sorted = new ArrayList<>(courses);
                sorted.sort(Comparator.comparingInt((Course c) -> degrees.getOrDefault(c.getId(), 0)).reversed()
                        .thenComparingInt(c -> c2s.getOrDefault(c.getId(), Collections.emptySet()).size())
                        .reversed());
                return sorted;
        }

        private List<List<Classroom>> findRoomCandidates(Course c, List<Classroom> classrooms, int needed, RoomComboGenerator rcg) {
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
                        logError(c.getId(), "Infrastructure Error: Not enough total capacity for " + needed + " students.");
                }
                return candidates;
        }

        private boolean attemptPlace(Course c, PartialSchedule schedule, List<Timeslot> slots,
                                     List<List<Classroom>> candidates, ConstraintSet constraints) {
                if (slots == null || candidates == null) return false;
                for (List<Classroom> rooms : candidates) {
                        for (Timeslot t : slots) {
                                Candidate cand = new Candidate(c.getId(), t, rooms);
                                if (constraints.ok(schedule, cand)) {
                                        schedule.addPlacement(new Placement(c.getId(), t, rooms));
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
                if (currentIds.isEmpty()) return false;

                int removeCount = Math.min(3, currentIds.size());
                List<String> victims = currentIds.subList(currentIds.size() - removeCount, currentIds.size());
                List<Placement> originalPlacements = new ArrayList<>();

                // Kurbanları geçici olarak çıkar
                for (String vid : victims) originalPlacements.add(schedule.removePlacement(vid));

                // Zor dersi yerleştirmeyi dene
                boolean mainPlaced = attemptPlace(c, schedule, cSlots, cCandidates, constraints);
                boolean allRestored = true;
                List<Placement> restoredPlacements = new ArrayList<>();

                if (mainPlaced) {
                        // Kurbanları geri yerleştirmeyi dene (Zaman kaydırma ile)
                        for (Placement p : originalPlacements) {
                                // Kurbanın sadece orijinal oda grubunu kullanıp farklı zaman arıyoruz (Basitleştirme)
                                List<List<Classroom>> singleCandidateList = List.of(p.getClassrooms());
                                if (attemptPlace(new Course(p.getCourseId(), 0), schedule, allSlots.get(p.getCourseId()), singleCandidateList, constraints)) {
                                        // Placement nesnesine erişemiyoruz ama schedule'a eklendi.
                                        // Rollback için schedule'dan son ekleneni bulmamız gerekebilir veya logic'e güveneceğiz.
                                        // Pratik çözüm: attemptPlace schedule'a ekler.
                                } else {
                                        allRestored = false;
                                        break;
                                }
                        }
                }

                // Karar Anı: Hepsi yerleşti mi?
                if (mainPlaced && allRestored) {
                        return true; // İşlem başarılı
                } else {
                        // ROLLBACK: Her şeyi geri al
                        if (mainPlaced) schedule.removePlacement(c.getId());

                        // Yeni yerleşen kurbanları temizle (Burası biraz trikli, schedule'dan silmek lazım)
                        // Basit Rollback: Şu an schedule'da olan ve victims listesinde olanları sil
                        for(String vid : victims) schedule.removePlacement(vid);

                        // Orijinalleri geri yükle
                        for (Placement p : originalPlacements) schedule.addPlacement(p);
                        return false;
                }
        }

        private void analyzeFailure(Course c, PartialSchedule schedule, List<Timeslot> slots,
                                    List<List<Classroom>> candidates, ConstraintSet constraints) {
                Map<String, Integer> reasons = new HashMap<>();
                if (slots != null) {
                        for (List<Classroom> rooms : candidates) {
                                for (Timeslot t : slots) {
                                        constraints.explain(schedule, new Candidate(c.getId(), t, rooms))
                                                .forEach(r -> reasons.put(r, reasons.getOrDefault(r, 0) + 1));
                                }
                        }
                }

                String msg = reasons.isEmpty() ? "Configuration Error: No valid timeslots." :
                        "Constraint Error: " + reasons.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();

                logError(c.getId(), msg);
        }

        private Map<String, List<StudentExam>> finalizeSchedule(PartialSchedule schedule,
                                                                Map<String, Set<String>> c2s,
                                                                Map<String, List<StudentExam>> results) {
                StudentDistributor distributor = new StudentDistributor();
                System.out.println("Finalizing schedule...");

                for (Placement p : schedule.getPlacements().values()) {
                        List<String> sList = new ArrayList<>(c2s.getOrDefault(p.getCourseId(), Collections.emptySet()));
                        List<StudentExam> exams = distributor.assign(p.getCourseId(), p.getTimeslot(), p.getClassrooms(), sList, 42L);

                        for (StudentExam se : exams) {
                                DBManager.insertSchedule(se);
                                results.computeIfAbsent(se.getStudentId(), k -> new ArrayList<>()).add(se);
                        }
                }
                return results;
        }

        private void markUnknownFailures(List<Course> courses, PartialSchedule schedule) {
                for (Course c : courses) {
                        if (!schedule.contains(c.getId()) && !unscheduledReasons.containsKey(c.getId())) {
                                unscheduledReasons.put(c.getId(), "Skipped (Unknown Reason)");
                        }
                }
        }

        private void logError(String courseId, String msg) {
                unscheduledReasons.put(courseId, msg);
                DBManager.logConflict(courseId, msg);
        }
}