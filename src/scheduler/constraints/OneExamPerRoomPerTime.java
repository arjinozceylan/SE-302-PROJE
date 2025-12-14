package scheduler.constraints;

import scheduler.model.Placement;
import scheduler.model.Timeslot;
import scheduler.model.Classroom;

import java.util.Map;

public class OneExamPerRoomPerTime implements Constraint {

    @Override
    public boolean test(PartialSchedule state, Candidate cand) {
        // Mevcut yerleşimler
        for (Map.Entry<String, Placement> e : state.getPlacements().entrySet()) {
            Placement p = e.getValue();

            // Zaman çakışıyor mu?
            if (overlap(p.getTimeslot(), cand.timeslot)) {
                // Ortak sınıf var mı?
                for (Classroom rNew : cand.classrooms) {
                    for (Classroom rOld : p.getClassrooms()) {
                        if (rNew.getId().equals(rOld.getId())) {
                            return false; // aynı anda aynı sınıf kullanılamaz
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public String getViolationMessage() {
        return "Room is already occupied at that time";
    }

    private boolean overlap(Timeslot a, Timeslot b) {
        if (!a.getDate().equals(b.getDate()))
            return false;
        // [start,end) aralıkları kesişiyor mu?
        boolean endsAfterStart = a.getEnd().isAfter(b.getStart());
        boolean startsBeforeEnd = a.getStart().isBefore(b.getEnd());
        return endsAfterStart && startsBeforeEnd;
    }
}