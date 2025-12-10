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
                        for (Classroom r : classrooms)
                                sum += r.getCapacity();
                        avgCapacity = sum / classrooms.size();
                }

                // 2. Conflict Graph
                ConflictGraphBuilder gb = new ConflictGraphBuilder();
                Map<String, Set<String>> courseToStudents = gb.buildCourseToStudents(enrollments);
                Map<String, Integer> degree = gb.buildDegrees(courseToStudents);

                // 3. Dersleri Sırala
                List<Course> orderedCourses = new ArrayList<>(courses);
                Map<String, Integer> courseSize = new HashMap<>();
                courseToStudents.forEach((k, v) -> courseSize.put(k, v.size()));

                orderedCourses.sort(
                                Comparator.comparingInt((Course c) -> degree.getOrDefault(c.getId(), 0)).reversed()
                                                .thenComparingInt(c -> courseSize.getOrDefault(c.getId(), 0))
                                                .reversed());

                // 4. Timeslot Üret
                TimeslotBuilder tsb = new TimeslotBuilder();
                Map<String, List<Timeslot>> slotsPerCourse = new HashMap<>();
                for (Course c : orderedCourses) {
                        slotsPerCourse.put(c.getId(), tsb.build(dayWindows, c.getDurationMinutes()));
                }

                // 5. Yerleştirme Mantığı
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

                        // Eğer dersin özel bir kapasite isteği varsa, sadece o odaları kullan.
                        List<Classroom> availableRooms;
                        if (c.getMinRoomCapacity() > 0) {
                                availableRooms = classrooms.stream()
                                                .filter(r -> r.getCapacity() >= c.getMinRoomCapacity())
                                                .collect(Collectors.toList());

                                if (availableRooms.isEmpty()) {
                                        String msg = "Custom Rule Error: No rooms match min capacity of "
                                                        + c.getMinRoomCapacity();
                                        unscheduledReasons.put(c.getId(), msg);
                                        DBManager.logConflict(c.getId(), msg);
                                        continue; // Bu dersi atla
                                }
                        } else {
                                availableRooms = classrooms; // Kısıtlama yoksa hepsi
                        }

                        // --- ROOM BALANCING ---
                        boolean preferLargeRoomsFirst = (avgCapacity == 0) || (need >= avgCapacity);

                        List<Classroom> pickedGreedy = rcg.generateGreedyOrdered(availableRooms, need,
                                        preferLargeRoomsFirst);

                        List<List<Classroom>> roomCandidates = new ArrayList<>();
                        if (RoomComboGenerator.totalCapacity(pickedGreedy) >= need) {
                                roomCandidates.add(pickedGreedy);
                        }
                        roomCandidates.addAll(rcg.generateMinimalCombos(availableRooms, need, 50));

                        if (roomCandidates.isEmpty()) {
                                String msg = "Not enough total classroom capacity ("
                                                + (c.getMinRoomCapacity() > 0 ? "with custom filter" : "") + ") for "
                                                + need + " students.";
                                unscheduledReasons.put(c.getId(), msg);
                                DBManager.logConflict(c.getId(), msg);
                                continue;
                        }

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

                        // --- BACKTRACKING ---
                        if (!placed) {
                                List<String> lastCourses = new ArrayList<>(schedule.getPlacements().keySet())
                                                .subList(Math.max(0, schedule.getPlacements().size() - 3),
                                                                schedule.getPlacements().size());

                                List<Placement> removed = new ArrayList<>();
                                for (String lastC : lastCourses)
                                        removed.add(schedule.removePlacement(lastC));

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
                                        for (Placement p : removed)
                                                if (p != null)
                                                        schedule.addPlacement(p);

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

                // Fallback Unscheduled Check
                for (Course cAll : courses) {
                        if (!unscheduledReasons.containsKey(cAll.getId())
                                        && !schedule.getPlacements().containsKey(cAll.getId())) {
                                unscheduledReasons.put(cAll.getId(), "Unscheduled (Unknown reason).");
                        }
                }

                // 6. Student Assignment
                StudentDistributor distributor = new StudentDistributor();
                for (Placement p : schedule.getPlacements().values()) {
                        Set<String> studentIds = courseToStudents.get(p.getCourseId());
                        if (studentIds == null)
                                continue;

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
}