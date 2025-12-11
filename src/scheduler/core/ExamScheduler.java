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

                // 1. UI'dan gün penceresi gelmediyse dur
                if (dayWindows == null || dayWindows.isEmpty()) {
                        System.out.println("No day windows provided, skipping scheduling.");
                        return results;
                }

                // --- ODA KAPASİTE İSTATİSTİĞİ ---
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
                                                .thenComparingInt(c -> courseSize.getOrDefault(c.getId(), 0))
                                                .reversed());

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
                                .add(new NoStudentClashAndMinGap(courseToStudents, SchedulingConfig.MIN_GAP_MINUTES))
                                .add(new MaxExamsPerDay(courseToStudents, SchedulingConfig.MAX_EXAMS_PER_DAY));

                PartialSchedule schedule = new PartialSchedule();

                for (Course c : orderedCourses) {
                        int need = courseSize.getOrDefault(c.getId(), 0);

                        // Hiç öğrenci yoksa: sebebi kaydet ve bu dersi planlama
                        if (need == 0) {
                                String msg = "No enrollments found for this course (0 students).";
                                unscheduledReasons.put(c.getId(), msg);
                                DBManager.logConflict(c.getId(), msg);
                                continue;
                        }

                        // =================================================================
                        // ÖZEL KAPASİTE FİLTRESİ (MIN ve MAX)
                        // =================================================================
                        List<Classroom> availableRooms = new ArrayList<>(classrooms);

                        // 1. Min Kapasite Kontrolü
                        if (c.getMinRoomCapacity() > 0) {
                                availableRooms = availableRooms.stream()
                                                .filter(r -> r.getCapacity() >= c.getMinRoomCapacity())
                                                .collect(Collectors.toList());
                        }

                        // 2. Max Kapasite Kontrolü
                        if (c.getMaxRoomCapacity() > 0) {
                                availableRooms = availableRooms.stream()
                                                .filter(r -> r.getCapacity() <= c.getMaxRoomCapacity())
                                                .collect(Collectors.toList());
                        }

                        // Filtreleme sonucu oda kalmadıysa hata verip geç
                        if (availableRooms.isEmpty()) {
                                String msg = "Custom Rule Error: No rooms match the capacity range ("
                                                + c.getMinRoomCapacity() + " - "
                                                + (c.getMaxRoomCapacity() > 0 ? c.getMaxRoomCapacity() : "Any") + ")";
                                unscheduledReasons.put(c.getId(), msg);
                                DBManager.logConflict(c.getId(), msg);
                                continue;
                        }

                        // --- ROOM BALANCING KARARI ---
                        boolean preferLargeRoomsFirst = (avgCapacity == 0) || (need >= avgCapacity);

                        List<Classroom> pickedGreedy = rcg.generateGreedyOrdered(availableRooms, need,
                                        preferLargeRoomsFirst);

                        List<List<Classroom>> roomCandidates = new ArrayList<>();

                        if (RoomComboGenerator.totalCapacity(pickedGreedy) >= need) {
                                roomCandidates.add(pickedGreedy);
                        }

                        // 2. Daha verimli kombinasyonlar
                        roomCandidates.addAll(rcg.generateMinimalCombos(availableRooms, need, 50));

                        if (roomCandidates.isEmpty()) {
                                String rangeInfo = (c.getMinRoomCapacity() > 0 || c.getMaxRoomCapacity() > 0)
                                                ? " (with custom filters)"
                                                : "";
                                String msg = "Not enough total classroom capacity" + rangeInfo + " for " + need
                                                + " students.";
                                unscheduledReasons.put(c.getId(), msg);
                                DBManager.logConflict(c.getId(), msg);
                                continue;
                        }

                        // --- ZAMAN + ODA ARAMASI ---
                        boolean placed = false;
                        List<Timeslot> slots = slotsPerCourse.get(c.getId());

                        if (slots != null) {
                                for (List<Classroom> roomSet : roomCandidates) {
                                        for (Timeslot t : slots) {
                                                Candidate cand = new Candidate(c.getId(), t, roomSet);

                                                if (constraints.ok(schedule, cand)) {
                                                        schedule.addPlacement(new Placement(c.getId(), t, roomSet));
                                                        placed = true;
                                                        break;
                                                }
                                        }
                                        if (placed)
                                                break;
                                }
                        }

                        // ---------- BACKTRACKING BAŞLANGIÇ ----------
                        if (!placed) {
                                List<String> lastCourses = new ArrayList<>(schedule.getPlacements().keySet())
                                                .subList(Math.max(0, schedule.getPlacements().size() - 3),
                                                                schedule.getPlacements().size());

                                List<Placement> removed = new ArrayList<>();
                                for (String lastC : lastCourses) {
                                        removed.add(schedule.removePlacement(lastC));
                                }

                                for (List<Classroom> roomSet : roomCandidates) {
                                        for (Timeslot t : slots) {
                                                Candidate cand = new Candidate(c.getId(), t, roomSet);
                                                if (constraints.ok(schedule, cand)) {
                                                        schedule.addPlacement(new Placement(c.getId(), t, roomSet));
                                                        placed = true;
                                                        break;
                                                }
                                        }
                                        if (placed)
                                                break;
                                }

                                if (!placed) {
                                        for (Placement p : removed) {
                                                if (p != null)
                                                        schedule.addPlacement(p);
                                        }
                                        String msg = "No feasible room+time combination (Constraints/Gap/MaxDaily).";
                                        unscheduledReasons.put(c.getId(), msg);
                                        DBManager.logConflict(c.getId(), msg);
                                } else {
                                        for (String victimId : lastCourses) {
                                                if (!victimId.equals(c.getId())
                                                                && !schedule.getPlacements().containsKey(victimId)
                                                                && !unscheduledReasons.containsKey(victimId)) {
                                                        String msg = "Unscheduled due to backtracking conflict.";
                                                        unscheduledReasons.put(victimId, msg);
                                                        DBManager.logConflict(victimId, msg);
                                                }
                                        }
                                }
                        }
                }

                // Fallback: Yerleşmemiş ama sebebi olmayanlar
                for (Course cAll : courses) {
                        if (!unscheduledReasons.containsKey(cAll.getId())) {
                                if (!schedule.getPlacements().containsKey(cAll.getId())) {
                                        unscheduledReasons.put(cAll.getId(), "Unscheduled (Unknown reason).");
                                }
                        }
                }

                // 6. Öğrencileri Koltuklara Ata
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

                        for (StudentExam se : assignments) {
                                DBManager.insertSchedule(se);
                                results.computeIfAbsent(se.getStudentId(), k -> new ArrayList<>()).add(se);
                        }
                }

                System.out.println("Scheduler finished. Assigned exams for " + results.size() + " students.");
                return results;
        }
}