package scheduler.core;

import scheduler.model.*;
import scheduler.assign.StudentDistributor;
import scheduler.config.SchedulingConfig;
import scheduler.constraints.NoStudentClashAndMinGap;
import scheduler.constraints.MaxExamsPerDay;
import scheduler.config.SchedulingConfig;
import scheduler.constraints.Candidate;
import scheduler.constraints.PartialSchedule;
import scheduler.constraints.ConstraintSet;
import scheduler.constraints.OneExamPerRoomPerTime;

import java.util.*;

public class ExamScheduler {
    public void run(List<Student> students,
                    List<Course> courses,
                    List<Enrollment> enrollments,
                    List<Classroom> classrooms) {
        System.out.println("Exam scheduler is running...");
        System.out.printf("Students=%d, Courses=%d, Enrollments=%d, Classrooms=%d%n",
                students.size(), courses.size(), enrollments.size(), classrooms.size());







        // === TEST: TimeslotBuilder ===
        /*System.out.println("\n--- TimeslotBuilder Test ---");

        List<DayWindow> testWindows = List.of(
                new DayWindow(
                        java.time.LocalDate.of(2025, 10, 15),
                        List.of(new TimeRange(java.time.LocalTime.of(9, 0),
                                java.time.LocalTime.of(12, 0)))
                )
        );

        TimeslotBuilder builder = new TimeslotBuilder();
        List<Timeslot> slots = builder.build(testWindows, 120);

        for (Timeslot t : slots) {
            System.out.printf("Date=%s, Start=%s, End=%s%n",
                    t.getDate(), t.getStart(), t.getEnd());
        }
        System.out.println("--- End of Test ---\n");
        // === TEST END ===
        // === CONFLICT GRAPH TEST ===
        System.out.println("--- ConflictGraph Test ---");

// Küçük demo: S1 hem C1 hem C2'de; S2 hem C2 hem C3'te → C1–C2 ve C2–C3 kenarları oluşur.
        // S1 sadece C1'de; S2 hem C2 hem C3'te; S3 C3'te
        List<Enrollment> demo = List.of(
                new Enrollment("S1", "C1"),
                new Enrollment("S2", "C2"),
                new Enrollment("S2", "C3"),
                new Enrollment("S3", "C3")
        );

        ConflictGraphBuilder cgb = new ConflictGraphBuilder();
        Map<String, Set<String>> c2s = cgb.buildCourseToStudents(demo);
        Map<String, Integer> deg = cgb.buildDegrees(c2s);

// Dereceleri yazdır
        for (var e : deg.entrySet()) {
            System.out.printf("Course=%s, degree=%d%n", e.getKey(), e.getValue());
        }

// Kenarları yazdır (aynı anda yapılamaz çiftleri)
        List<String> coursesList = new ArrayList<>(c2s.keySet());
        for (int i = 0; i < coursesList.size(); i++) {
            for (int j = i + 1; j < coursesList.size(); j++) {
                String a = coursesList.get(i), b = coursesList.get(j);
                if (!Collections.disjoint(c2s.get(a), c2s.get(b))) {
                    System.out.printf("EDGE: %s <-> %s%n", a, b);
                }
            }
        }
        System.out.println("--- End ConflictGraph Test ---\n");
// === TEST 2 END ===
        // === RoomComboGenerator Test ===
        System.out.println("--- RoomCombo Test ---");
        RoomComboGenerator rcg = new RoomComboGenerator();

// Örnek 1: 130 kişilik sınav için ilk 5 kombinasyon
        List<List<Classroom>> combos130 = rcg.generateMinimalCombos(classrooms, 130, 5);
        System.out.println("Needed=130");
        for (List<Classroom> combo : combos130) {
            int sum = combo.stream().mapToInt(Classroom::getCapacity).sum();
            System.out.print("Rooms: ");
            for (Classroom r : combo) System.out.print(r.getId() + "(" + r.getCapacity() + ") ");
            System.out.println(" -> total=" + sum);
        }

// Örnek 2: 300 kişilik sınav için ilk 5 kombinasyon
        List<List<Classroom>> combos300 = rcg.generateMinimalCombos(classrooms, 300, 5);
        System.out.println("Needed=300");
        for (List<Classroom> combo : combos300) {
            int sum = combo.stream().mapToInt(Classroom::getCapacity).sum();
            System.out.print("Rooms: ");
            for (Classroom r : combo) System.out.print(r.getId() + "(" + r.getCapacity() + ") ");
            System.out.println(" -> total=" + sum);
        }
        System.out.println("--- End RoomCombo Test ---\n");
*/





        // === INTEGRATION SMOKE TEST ===
      /*  System.out.println("\n=== INTEGRATION SMOKE TEST ===");

// 1) Takvim: 1 gün, iki aralık
        List<DayWindow> day = List.of(
                new DayWindow(java.time.LocalDate.of(2025, 10, 15),
                        List.of(
                                new TimeRange(java.time.LocalTime.of(9, 0),  java.time.LocalTime.of(12, 0)),
                                new TimeRange(java.time.LocalTime.of(13, 0), java.time.LocalTime.of(17, 0))
                        ))
        );

// 2) Dersler ve süreleri (test için lokal)
        List<Course> demoCourses = List.of(
                new Course("MATH101", 120),  // büyük
                new Course("PHY101",  90),   // orta
                new Course("HIST201", 60)    // küçük
        );

// 3) Yaklaşık öğrenci sayıları (yerleştirme için ihtiyaç)
        Map<String,Integer> courseSize = new HashMap<>();
        courseSize.put("MATH101", 420);
        courseSize.put("PHY101",  180);
        courseSize.put("HIST201",  70);

// 4) Çakışma grafiği göstermek için küçük enrollment demo (kenarlar: MATH101–PHY101, PHY101–HIST201)
        List<Enrollment> demoEnroll = List.of(
                new Enrollment("S1","MATH101"),
                new Enrollment("S1","PHY101"),
                new Enrollment("S2","PHY101"),
                new Enrollment("S2","HIST201"),
                new Enrollment("S3","HIST201")
        );
        ConflictGraphBuilder cgb = new ConflictGraphBuilder();
        Map<String, Set<String>> c2s = cgb.buildCourseToStudents(demoEnroll);
        Map<String, Integer> deg = cgb.buildDegrees(c2s);
        System.out.println("Conflict degrees: " + deg);

// 5) Her kurs için uygun timeslot’ları üret
        TimeslotBuilder tsb = new TimeslotBuilder();
        Map<String,List<Timeslot>> slotsPer = new HashMap<>();
        for (Course c : demoCourses) {
            slotsPer.put(c.getId(), tsb.build(day, c.getDurationMinutes()));
        }

// 6) Oda seçimi: büyükler → büyükten, küçükler → küçükten
        int maxCap = 0;
        for (Classroom r : classrooms) maxCap = Math.max(maxCap, r.getCapacity());
        RoomComboGenerator rcg = new RoomComboGenerator();

// 7) Kısıt seti ve kısmi çizelge
        ConstraintSet constraints = new ConstraintSet()
                .add(new OneExamPerRoomPerTime()); // tek kısıt: aynı anda aynı sınıf kullanılamaz
        PartialSchedule state = new PartialSchedule();

// 8) Dersleri kalabalıktan aza sırala ve yerleştir
        List<Course> ordered = new ArrayList<>(demoCourses);
        ordered.sort(Comparator.comparingInt((Course c) -> courseSize.getOrDefault(c.getId(),0)).reversed());

        for (Course c : ordered) {
            int need = courseSize.get(c.getId());
            boolean preferLargeFirst = (need >= maxCap);
            List<Classroom> picked = rcg.generateGreedyOrdered(classrooms, need, preferLargeFirst);

            int total = 0; for (Classroom r : picked) total += r.getCapacity();
            System.out.printf("Pick rooms for %s size=%d preferLarge=%s -> totalCap=%d%n",
                    c.getId(), need, String.valueOf(preferLargeFirst), total);
            if (total < need) {
                System.out.println("  WARNING: capacity insufficient, skipping placement demo for " + c.getId());
                continue;
            }

            // Uygun ilk timeslot’u bul ve yerleştir
            boolean placed = false;
            for (Timeslot t : slotsPer.get(c.getId())) {
                Candidate cand = new Candidate(c.getId(), t, picked);
                if (constraints.ok(state, cand)) {
                    state.addPlacement(new Placement(c.getId(), t, picked));
                    System.out.printf("  PLACED %s at %s %s–%s using %d rooms%n",
                            c.getId(), t.getDate(), t.getStart(), t.getEnd(), picked.size());
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                System.out.println("  NO FEASIBLE TIMESLOT for " + c.getId());
            }
        }

// 9) Son yerleşimleri yazdır
        System.out.println("--- FINAL PLACEMENTS ---");
        for (java.util.Map.Entry<String, Placement> e : state.getPlacements().entrySet()) {
            Placement p = e.getValue();
            System.out.printf("%s -> %s %s–%s | rooms: ",
                    p.getCourseId(), p.getTimeslot().getDate(), p.getTimeslot().getStart(), p.getTimeslot().getEnd());
            for (Classroom r : p.getClassrooms()) System.out.print(r.getId() + "(" + r.getCapacity() + ") ");
            System.out.println();
        }
        System.out.println("=== END SMOKE TEST ===\n");*/





       /*

// === STUDENT DISTRIBUTOR TEST (seed=42) ===
        // --- PREP: c2s, state ve en az bir Placement oluştur ---
        ConflictGraphBuilder cgbPrep = new ConflictGraphBuilder();
        Map<String, Set<String>> c2s = cgbPrep.buildCourseToStudents(enrollments);

// Kısmi çizelge
        PartialSchedule state = new PartialSchedule();

// Minimum: bir yerleşim ekle ki dağıtıcı testinde veri olsun
        Timeslot tPrep = new Timeslot(
                java.time.LocalDate.of(2025, 10, 15),
                java.time.LocalTime.of(9, 0),
                java.time.LocalTime.of(11, 0)
        );
// Mevcut sınıflardan ilk 1-2 tanesini kullan
        List<Classroom> pickedPrep = classrooms.size() >= 2 ? classrooms.subList(0, 2) : classrooms;
        state.addPlacement(new Placement("Course_01", tPrep, pickedPrep));
        System.out.println("\n--- StudentDistributor Test ---");

        StudentDistributor dist = new StudentDistributor();

// courseId -> students (c2s) zaten yukarıda üretildi; yoksa demo doldur:
        if (c2s == null || c2s.isEmpty()) {
            c2s = new java.util.HashMap<>();
            c2s.put("MATH101", new java.util.HashSet<>(java.util.Arrays.asList("S1","S2","S3","S4","S5","S6")));
        }

// Her yerleşim için öğrencileri sınıflara ve sıralara ata
        for (java.util.Map.Entry<String, Placement> e : state.getPlacements().entrySet()) {
            String courseId = e.getKey();
            Placement p = e.getValue();

            java.util.Set<String> set = c2s.getOrDefault(courseId, java.util.Collections.emptySet());
            java.util.List<String> studentsForCourse = new java.util.ArrayList<>(set);

            java.util.List<StudentExam> assigned = dist.assign(
                    courseId,
                    p.getTimeslot(),
                    p.getClassrooms(),
                    studentsForCourse,
                    42L // sabit seed
            );

            // Özet yazdır
            System.out.printf("Course=%s assigned=%d/%d%n",
                    courseId, assigned.size(), studentsForCourse.size());

            // Oda kırılımı ve ilk birkaç öğrenciyi yazdır
            java.util.Map<String, Integer> perRoom = new java.util.HashMap<>();
            int preview = 0;
            for (StudentExam se : assigned) {
                perRoom.put(se.getClassroomId(), perRoom.getOrDefault(se.getClassroomId(), 0) + 1);
                if (preview < 5) { // ilk 5 örnek göster
                    System.out.printf("  %s -> %s %s–%s | room=%s seat=%d%n",
                            se.getStudentId(), se.getCourseId(),
                            se.getTimeslot().getStart(), se.getTimeslot().getEnd(),
                            se.getClassroomId(), se.getSeatNo());
                    preview++;
                }
            }
            System.out.print("  Room loads: ");
            for (java.util.Map.Entry<String,Integer> rr : perRoom.entrySet()) {
                System.out.printf("%s=%d ", rr.getKey(), rr.getValue());
            }
            System.out.println();

            // Kapasite yetersizliği uyarısı
            if (assigned.size() < studentsForCourse.size()) {
                System.out.println("  WARNING: capacity insufficient for " + courseId);
            }
        }
        System.out.println("--- End StudentDistributor Test ---\n");
*/




        // 1) Takvim penceresi (şimdilik sabit; ileride dosyadan okunacak)
        List<DayWindow> dayWindows = List.of(
                new DayWindow(
                        java.time.LocalDate.of(2025, 10, 15),
                        List.of(
                                new TimeRange(java.time.LocalTime.of(9, 0),  java.time.LocalTime.of(12, 0)),
                                new TimeRange(java.time.LocalTime.of(13, 0), java.time.LocalTime.of(17, 0))
                        )
                )
        );

        // 2) courseId -> öğrenciler ve dereceler
        ConflictGraphBuilder graphBuilder = new ConflictGraphBuilder();
        Map<String, Set<String>> courseToStudents = graphBuilder.buildCourseToStudents(enrollments);
        Map<String, Integer> degree = graphBuilder.buildDegrees(courseToStudents);
        System.out.println("\n--- Debug: courses list ---");
        for (Course c : courses) {
            System.out.println("Course in list: [" + c.getId() + "]");
        }
        System.out.println("--- End courses list ---");

        System.out.println("\n--- Debug: courseToStudents ---");
        for (Map.Entry<String, Set<String>> e : courseToStudents.entrySet()) {
            System.out.println("courseToStudents key: [" + e.getKey() + "] -> " + e.getValue().size() + " students");
        }
        System.out.println("--- End courseToStudents ---\n");
        // 3) Ders başına öğrenci sayısı
        Map<String, Integer> courseSize = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : courseToStudents.entrySet()) {
            courseSize.put(e.getKey(), e.getValue().size());
        }

        // 4) Dersleri: önce yüksek dereceli, sonra kalabalık olandan başlayarak sırala
        List<Course> orderedCourses = new ArrayList<>(courses);
        orderedCourses.sort(
                Comparator
                        .comparingInt((Course c) -> degree.getOrDefault(c.getId(), 0)).reversed()
                        .thenComparingInt(c -> courseSize.getOrDefault(c.getId(), 0)).reversed()
        );

        // 5) Her ders için uygun timeslot listelerini hazırla
        TimeslotBuilder timeslotBuilder = new TimeslotBuilder();
        Map<String, List<Timeslot>> slotsPerCourse = new HashMap<>();
        for (Course c : orderedCourses) {
            List<Timeslot> ts = timeslotBuilder.build(dayWindows, c.getDurationMinutes());
            slotsPerCourse.put(c.getId(), ts);
        }

        // 6) En büyük sınıf kapasitesini bul
        int maxCap = 0;
        for (Classroom r : classrooms) {
            if (r.getCapacity() > maxCap) {
                maxCap = r.getCapacity();
            }
        }

        RoomComboGenerator roomCombo = new RoomComboGenerator();

        // 7) Kısıt seti ve kısmi çizelge
        ConstraintSet constraints = new ConstraintSet()
                .add(new OneExamPerRoomPerTime())
                .add(new NoStudentClashAndMinGap(courseToStudents, SchedulingConfig.MIN_GAP_MINUTES))
                .add(new MaxExamsPerDay(courseToStudents, SchedulingConfig.MAX_EXAMS_PER_DAY));
        PartialSchedule state = new PartialSchedule();

        System.out.println("\n--- Placement loop start ---");

        // 8) Yerleştirme döngüsü
        for (Course c : orderedCourses) {
            String courseId = c.getId();
            int need = courseSize.getOrDefault(courseId, 0);
            if (need == 0) {
                System.out.printf("Course %s has no students, skipping%n", courseId);
                continue;
            }

            boolean preferLargeFirst = (need >= maxCap);
            List<Classroom> pickedRooms = roomCombo.generateGreedyOrdered(classrooms, need, preferLargeFirst);
            int totalCap = RoomComboGenerator.totalCapacity(pickedRooms);

            System.out.printf("Course %s size=%d preferLarge=%s totalCap=%d | rooms: ",
                    courseId, need, String.valueOf(preferLargeFirst), totalCap);

            for (Classroom r : pickedRooms) {
                System.out.print(r.getId() + "(" + r.getCapacity() + ") ");
            }
            System.out.println();

            if (totalCap < need) {
                System.out.printf("  WARNING: capacity insufficient for course %s%n", courseId);
                continue;
            }

            List<Timeslot> possibleSlots = slotsPerCourse.get(courseId);
            if (possibleSlots == null || possibleSlots.isEmpty()) {
                System.out.printf("  No timeslots available for course %s%n", courseId);
                continue;
            }

            boolean placed = false;
            for (Timeslot t : possibleSlots) {
                Candidate cand = new Candidate(courseId, t, pickedRooms);
                if (constraints.ok(state, cand)) {
                    state.addPlacement(new Placement(courseId, t, pickedRooms));
                    System.out.printf("  PLACED %s at %s %s–%s using %d rooms%n",
                            courseId, t.getDate(), t.getStart(), t.getEnd(), pickedRooms.size());
                    placed = true;
                    break;
                }
            }

            if (!placed) {
                System.out.printf("  NO FEASIBLE TIMESLOT for course %s%n", courseId);
            }
        }

        System.out.println("--- Final placements ---");
        for (Map.Entry<String, Placement> e : state.getPlacements().entrySet()) {
            Placement p = e.getValue();
            System.out.printf("%s -> %s %s–%s | rooms: ",
                    p.getCourseId(), p.getTimeslot().getDate(), p.getTimeslot().getStart(), p.getTimeslot().getEnd());
            for (Classroom r : p.getClassrooms()) {
                System.out.print(r.getId() + "(" + r.getCapacity() + ") ");
            }
            System.out.println();
        }
        System.out.println("--- End placement loop ---");





        // TODO: 1. validate data
        // TODO: 2. generate timeslots
        // TODO: 3. build conflict graph
        // TODO: 4. place exams
        // TODO: 5. assign students
        // TODO: 6. output schedules
    }

}