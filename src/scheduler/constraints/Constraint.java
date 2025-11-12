package scheduler.constraints;

public interface Constraint {
    boolean test(PartialSchedule state, Candidate candidate);
}