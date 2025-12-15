package scheduler.constraints;

import scheduler.model.Placement;
import scheduler.model.Timeslot;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Her öğrenci için bir günde en fazla maxPerDay sınav olmasını sağlar.
 */
public class MaxExamsPerDay implements Constraint {

    private final Map<String, Set<String>> courseToStudents; // courseId -> öğrenciler
    private final int maxPerDay; // örn: 2

    public MaxExamsPerDay(Map<String, Set<String>> courseToStudents, int maxPerDay) {
        this.courseToStudents = courseToStudents;
        this.maxPerDay = maxPerDay;
    }

    @Override
    public boolean test(PartialSchedule state, Candidate cand) {
        Set<String> newStudents = courseToStudents.get(cand.courseId);
        if (newStudents == null || newStudents.isEmpty())
            return true;

        Timeslot newSlot = cand.timeslot;
        LocalDate day = newSlot.getDate();

        // O gün için: öğrenci -> o gün kaç sınavı var?
        Map<String, Integer> countPerStudent = new HashMap<>();

        for (Placement p : state.getPlacements().values()) {
            Timeslot oldSlot = p.getTimeslot();
            if (!oldSlot.getDate().equals(day)) {
                continue; // farklı gün, ilgilenmiyoruz
            }
            Set<String> oldStudents = courseToStudents.get(p.getCourseId());
            if (oldStudents == null || oldStudents.isEmpty()) {
                continue;
            }
            for (String sid : oldStudents) {
                countPerStudent.put(sid, countPerStudent.getOrDefault(sid, 0) + 1);
            }
        }

        // Yeni sınav eklendiğinde sayıları bir artırmış gibi düşün
        for (String sid : newStudents) {
            int current = countPerStudent.getOrDefault(sid, 0);
            if (current >= maxPerDay) {
                // Bu öğrenci için bu gün zaten maxPerDay sınav var,
                // bir tane daha ekleyemeyiz.
                return false;
            }
        }

        return true;
    }

    @Override
    public String getViolationMessage() {
        return "Daily exam limit per student exceeded";
    }

}