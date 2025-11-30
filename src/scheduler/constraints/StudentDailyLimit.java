package scheduler.constraints;

import scheduler.model.Placement;
import scheduler.model.Timeslot;

import java.util.Map;
import java.util.Set;

/**
 * Her öğrenci için:
 * Aynı günde en fazla MAX_EXAMS_PER_DAY sınav olmasına izin verir.
 */
public class StudentDailyLimit implements Constraint {

    private final Map<String, Set<String>> courseToStudents;
    private final int maxExamsPerDay;

    public StudentDailyLimit(Map<String, Set<String>> courseToStudents, int maxExamsPerDay) {
        this.courseToStudents = courseToStudents;
        this.maxExamsPerDay = maxExamsPerDay;
    }

    @Override
    public boolean test(PartialSchedule state, Candidate cand) {

        // Bu derse kayıtlı öğrenciler
        Set<String> candStudents = courseToStudents.get(cand.courseId);
        if (candStudents == null || candStudents.isEmpty()) {
            // Derse öğrenci yoksa kısıt ihlali yok
            return true;
        }

        // Her öğrenci için tek tek kontrol et
        for (String sid : candStudents) {

            int countSameDay = 0;

            // Zaten schedule'a konmuş tüm placement'ları dolaş
            for (Placement existing : state.getPlacements().values()) {

                Timeslot existingSlot = existing.getTimeslot();

                // Farklı günse önemli değil
                if (!existingSlot.getDate().equals(cand.timeslot.getDate())) {
                    continue;
                }

                // Bu placement'taki derse kimler kayıtlı?
                Set<String> existingStudents = courseToStudents.get(existing.getCourseId());
                if (existingStudents == null) continue;

                // Eğer bu öğrenci o derste de varsa, aynı gün bir sınavı daha var demektir
                if (existingStudents.contains(sid)) {
                    countSameDay++;

                    // Zaten bu öğrenci için o gün max sınav sayısına ulaştıysak,
                    // yeni placement'ı reddet
                    if (countSameDay >= maxExamsPerDay) {
                        return false;
                    }
                }
            }
        }

        // Hiçbir öğrenci max sınırını aşmıyorsa placement kabul
        return true;
    }
}