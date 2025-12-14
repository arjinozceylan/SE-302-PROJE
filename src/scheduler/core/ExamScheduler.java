package scheduler.core;

import scheduler.assign.StudentDistributor;
import scheduler.config.SchedulingConfig;
import scheduler.constraints.*;
import scheduler.model.*;
import scheduler.dao.*;

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
                Map<String, List<StudentExam>> results = new HashMap<>();
                unscheduledReasons.clear();

                if (dayWindows == null || dayWindows.isEmpty()) {
                        System.out.println("No day windows provided, skipping scheduling.");
                        return results;
                }

                // --- ODA KAPASİTE İSTATİSTİĞİ ---
                int avgCapacity = 0;
                if (classrooms != null && !classrooms.isEmpty()) {
                        int sum = 0;
                        for (Classroom r : classrooms) sum += r.getCapacity();
                        avgCapacity = sum / classrooms.size();
                }

                // 2. Conflict Graph
                ConflictGraphBuilder gb = new ConflictGraphBuilder();
                Map<String, Set<String>> courseToStudents = gb.buildCourseToStudents(enrollments);
                Map<String, Integer> degree = gb.buildDegrees(courseToStudents);

                // 3. Sıralama
                List<Course> orderedCourses = new ArrayList<>(courses);
                Map<String, Integer> courseSize = new HashMap<>();
                courseToStudents.forEach((k, v) -> courseSize.put(k, v.size()));

                orderedCourses.sort(
                        Comparator.comparingInt((Course c) -> degree.getOrDefault(c.getId(), 0)).reversed()
                                .thenComparingInt(c -> courseSize.getOrDefault(c.getId(), 0))
                                .reversed());

                // 4. Timeslots
                TimeslotBuilder tsb = new TimeslotBuilder();
                Map<String, List<Timeslot>> slotsPerCourse = new HashMap<>();
                for (Course c : orderedCourses) {
                        slotsPerCourse.put(c.getId(), tsb.build(dayWindows, c.getDurationMinutes()));
                }

                // 5. Yerleştirme
                RoomComboGenerator rcg = new RoomComboGenerator();

                ConstraintSet constraints = new ConstraintSet()
                        .add(new OneExamPerRoomPerTime())
                        .add(new NoStudentClashAndMinGap(courseToStudents, SchedulingConfig.MIN_GAP_MINUTES))
                        .add(new MaxExamsPerDay(courseToStudents, SchedulingConfig.MAX_EXAMS_PER_DAY));

                PartialSchedule schedule = new PartialSchedule();

                for (Course c : orderedCourses) {
                        int need = courseSize.getOrDefault(c.getId(), 0);

                        if (need == 0) {
                                String msg = "No enrollments found (0 students).";
                                unscheduledReasons.put(c.getId(), msg);
                                DBManager.logConflict(c.getId(), msg);
                                continue;
                        }

                        // --- ODA FİLTRELEME (Min/Max) ---
                        List<Classroom> availableRooms = new ArrayList<>(classrooms);
                        if (c.getMinRoomCapacity() > 0) {
                                availableRooms = availableRooms.stream()
                                        .filter(r -> r.getCapacity() >= c.getMinRoomCapacity())
                                        .collect(Collectors.toList());
                        }
                        if (c.getMaxRoomCapacity() > 0) {
                                availableRooms = availableRooms.stream()
                                        .filter(r -> r.getCapacity() <= c.getMaxRoomCapacity())
                                        .collect(Collectors.toList());
                        }

                        if (availableRooms.isEmpty()) {
                                String msg = "Configuration Error: No rooms match capacity range ("
                                        + c.getMinRoomCapacity() + " - "
                                        + (c.getMaxRoomCapacity() > 0 ? c.getMaxRoomCapacity() : "Any") + ")";
                                unscheduledReasons.put(c.getId(), msg);
                                DBManager.logConflict(c.getId(), msg);
                                continue;
                        }

                        // --- ODA KOMBİNASYONLARI ---
                        boolean preferLargeRoomsFirst = (avgCapacity == 0) || (need >= avgCapacity);
                        List<Classroom> pickedGreedy = rcg.generateGreedyOrdered(availableRooms, need, preferLargeRoomsFirst);
                        List<List<Classroom>> roomCandidates = new ArrayList<>();

                        if (RoomComboGenerator.totalCapacity(pickedGreedy) >= need) {
                                roomCandidates.add(pickedGreedy);
                        }
                        roomCandidates.addAll(rcg.generateMinimalCombos(availableRooms, need, 50));

                        if (roomCandidates.isEmpty()) {
                                String rangeInfo = (c.getMinRoomCapacity() > 0 || c.getMaxRoomCapacity() > 0) ? " (check custom filters)" : "";
                                String msg = "Infrastructure Error: Not enough total classroom capacity" + rangeInfo + " for " + need + " students.";
                                unscheduledReasons.put(c.getId(), msg);
                                DBManager.logConflict(c.getId(), msg);
                                continue;
                        }

                        // --- İLK DENEME (Normal Yerleştirme) ---
                        boolean placed = attemptPlace(c, schedule, slotsPerCourse.get(c.getId()), roomCandidates, constraints);

                        // --- BACKTRACKING (TRANSACTIONAL / YA HEPSİ YA HİÇ) ---
                        if (!placed) {
                                // 1. Son eklenen kurbanları belirle (Son 3 ders)
                                List<String> lastCourses = new ArrayList<>(schedule.getPlacements().keySet());
                                int removeCount = Math.min(3, lastCourses.size());

                                // Eğer hiç yerleşmiş ders yoksa backtracking yapamayız
                                if (removeCount > 0) {
                                        List<String> victimIds = lastCourses.subList(lastCourses.size() - removeCount, lastCourses.size());
                                        List<Placement> originalVictims = new ArrayList<>();

                                        // Kurbanların orijinal hallerini sakla ve programdan çıkar
                                        for (String vid : victimIds) {
                                                originalVictims.add(schedule.removePlacement(vid));
                                        }

                                        // 2. Zor dersi (c) yerleştirmeyi dene
                                        boolean currentPlaced = attemptPlace(c, schedule, slotsPerCourse.get(c.getId()), roomCandidates, constraints);

                                        // 3. Kurbanları tekrar yerleştirmeyi dene
                                        boolean allVictimsRestored = true;
                                        List<Placement> newVictimPlacements = new ArrayList<>(); // Geri yüklenenleri takip et

                                        if (currentPlaced) {
                                                for (Placement victim : originalVictims) {

                                                        Course vCourse = new Course(victim.getCourseId(), 0); // Dummy course object
                                                        // (Not: Gerçek course objesine gerek yok, ID yetiyor ama slots map'ten çekeceğiz)

                                                        List<Timeslot> vSlots = slotsPerCourse.get(victim.getCourseId());


                                                        boolean victimRestored = false;
                                                        if (vSlots != null) {
                                                                // Kurbanın kendi eski odalarını kullanmayı dene (Time shifting)
                                                                for (Timeslot vt : vSlots) {
                                                                        Candidate vCand = new Candidate(victim.getCourseId(), vt, victim.getClassrooms());
                                                                        if (constraints.ok(schedule, vCand)) {
                                                                                Placement newP = new Placement(victim.getCourseId(), vt, victim.getClassrooms());
                                                                                schedule.addPlacement(newP);
                                                                                newVictimPlacements.add(newP);
                                                                                victimRestored = true;
                                                                                break;
                                                                        }
                                                                }
                                                        }

                                                        if (!victimRestored) {
                                                                allVictimsRestored = false;
                                                                break; // Zincir koptu
                                                        }
                                                }
                                        }

                                        // 4. KARAR ANI (TRANSACTION CHECK)
                                        if (currentPlaced && allVictimsRestored) {
                                                // BAŞARILI: Zor ders girdi, eskiler de (belki başka saatte) yerleşti.
                                                // Onayla, devam et.
                                        } else {
                                                // BAŞARISIZ: "Bad Trade" -> Hepsini geri al (ROLLBACK)

                                                // a. Yeni dersi çıkar (eğer girdiyse)
                                                if (currentPlaced) schedule.removePlacement(c.getId());

                                                // b. Yeni yerleşen kurbanları çıkar
                                                for (Placement np : newVictimPlacements) schedule.removePlacement(np.getCourseId());

                                                // c. Kurbanları ESKİ HALLERİYLE geri koy (Garanti çalışır çünkü başta oradaydılar)
                                                for (Placement op : originalVictims) {
                                                        schedule.addPlacement(op);
                                                }

                                                // d. Backtracking başarısız olduğu için 'placed' hala false.
                                                // Hata analizi aşağıda yapılacak.
                                        }
                                }
                        }

                        // --- HATA ANALİZİ VE RAPORLAMA ---
                        if (!placed && !schedule.contains(c.getId())) {
                                // Burada detaylı analiz yapıyoruz
                                Map<String, Integer> failureReasons = new HashMap<>();
                                int totalAttempts = 0;

                                for (List<Classroom> roomSet : roomCandidates) {
                                        List<Timeslot> slots = slotsPerCourse.get(c.getId());
                                        if (slots != null) {
                                                for (Timeslot t : slots) {
                                                        Candidate cand = new Candidate(c.getId(), t, roomSet);
                                                        List<String> reasons = constraints.explain(schedule, cand);
                                                        for (String r : reasons) {
                                                                failureReasons.put(r, failureReasons.getOrDefault(r, 0) + 1);
                                                        }
                                                        totalAttempts++;
                                                }
                                        }
                                }

                                String finalMsg;
                                if (totalAttempts == 0) {
                                        finalMsg = "Configuration Error: No valid time slots (duration vs day limits).";
                                } else {
                                        String topReason = failureReasons.entrySet().stream()
                                                .max(Map.Entry.comparingByValue())
                                                .map(Map.Entry::getKey)
                                                .orElse("Unknown constraints");
                                        finalMsg = "Constraint Error: " + topReason;
                                        if (failureReasons.size() > 1) finalMsg += " (Also blocked by others)";
                                }

                                unscheduledReasons.put(c.getId(), finalMsg);
                                DBManager.logConflict(c.getId(), finalMsg);
                        }
                }

                // Fallback
                for (Course cAll : courses) {
                        if (!unscheduledReasons.containsKey(cAll.getId()) && !schedule.contains(cAll.getId())) {
                                unscheduledReasons.put(cAll.getId(), "Skipped (Unknown Reason).");
                        }
                }

                // 6. Student Assignment
                StudentDistributor distributor = new StudentDistributor();
                for (Placement p : schedule.getPlacements().values()) {
                        Set<String> studentIds = courseToStudents.get(p.getCourseId());
                        if (studentIds == null) continue;

                        List<StudentExam> assignments = distributor.assign(
                                p.getCourseId(), p.getTimeslot(), p.getClassrooms(),
                                new ArrayList<>(studentIds), SchedulingConfig.RANDOM_SEED);

                        for (StudentExam se : assignments) {
                                DBManager.insertSchedule(se);
                                results.computeIfAbsent(se.getStudentId(), k -> new ArrayList<>()).add(se);
                        }
                }

                System.out.println("Scheduler finished. Assigned exams for " + results.size() + " students.");
                return results;
        }

        // Yardımcı Metod: Verilen adayı yerleştirmeyi dener
        private boolean attemptPlace(Course c, PartialSchedule schedule, List<Timeslot> slots,
                                     List<List<Classroom>> roomCandidates, ConstraintSet constraints) {
                if (slots == null || roomCandidates == null) return false;

                for (List<Classroom> roomSet : roomCandidates) {
                        for (Timeslot t : slots) {
                                Candidate cand = new Candidate(c.getId(), t, roomSet);
                                if (constraints.ok(schedule, cand)) {
                                        schedule.addPlacement(new Placement(c.getId(), t, roomSet));
                                        return true;
                                }
                        }
                }
                return false;
        }
}