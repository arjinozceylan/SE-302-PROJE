package scheduler.constraints;

import scheduler.model.Placement;
import scheduler.model.Timeslot;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

public class NoStudentClashAndMinGap implements Constraint {
    private final Map<String, Set<String>> courseToStudents; // courseId -> öğrenciler
    private final int minGapMinutes;

    public NoStudentClashAndMinGap(Map<String, Set<String>> courseToStudents, int minGapMinutes) {
        this.courseToStudents = courseToStudents;
        this.minGapMinutes = minGapMinutes;
    }

    @Override
    public boolean test(PartialSchedule state, Candidate cand) {
        Set<String> sNew = courseToStudents.get(cand.courseId);
        if (sNew == null || sNew.isEmpty()) return true;

        for (Placement p : state.getPlacements().values()) {
            Set<String> sOld = courseToStudents.get(p.getCourseId());
            if (sOld == null || sOld.isEmpty()) continue;

            // Ortak öğrenci var mı?
            if (!java.util.Collections.disjoint(sNew, sOld)) {
                Timeslot a = p.getTimeslot();
                Timeslot b = cand.timeslot;

                if (!a.getDate().equals(b.getDate())) {
                    // farklı gün: sorun yok
                    continue;
                }

                // Zaman bindirmesi var mı?
                boolean overlap =
                        a.getEnd().isAfter(b.getStart()) &&
                                a.getStart().isBefore(b.getEnd());
                if (overlap) return false;

                // Min gap kontrolü (aynı gün)
                long gapAB = Math.min(
                        Math.abs(Duration.between(a.getEnd(), b.getStart()).toMinutes()),
                        Math.abs(Duration.between(b.getEnd(), a.getStart()).toMinutes())
                );
                if (gapAB < minGapMinutes) return false;
            }
        }
        return true;
    }
}