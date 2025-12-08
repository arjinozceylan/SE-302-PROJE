package scheduler.constraints;

import java.util.ArrayList;
import java.util.List;

public class ConstraintSet {
    private final List<Constraint> list = new ArrayList<>();

    public ConstraintSet add(Constraint c) {
        list.add(c);
        return this;
    }

    public boolean ok(PartialSchedule s, Candidate c) {
        for (Constraint k : list) {
            if (!k.test(s, c))
                return false;
        }
        return true;
    }

    public List<String> explain(PartialSchedule s, Candidate c) {
        List<String> reasons = new ArrayList<>();

        for (Constraint k : list) {
            if (!k.test(s, c)) {
                if (k instanceof OneExamPerRoomPerTime) {
                    reasons.add("Room is already occupied at that time");
                } else if (k instanceof NoStudentClashAndMinGap) {
                    reasons.add("Student clash or minimum gap between exams violated");
                } else if (k instanceof MaxExamsPerDay) {
                    reasons.add("Daily exam limit per student exceeded");
                } else {
                    reasons.add("Constraint failed: " + k.getClass().getSimpleName());
                }
            }
        }
        return reasons;
    }

}
