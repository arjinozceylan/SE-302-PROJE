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
                // ARTIK instanceof YOK! Herkes kendi mesajını bilir.
                reasons.add(k.getViolationMessage());
            }
        }
        return reasons;
    }
}