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
            if (!k.test(s, c)) return false;
        }
        return true;
    }
}