package scheduler.constraints;

public interface Constraint {
    // Kurala uyuyor mu?
    boolean test(PartialSchedule state, Candidate candidate);

    // Kural ihlal edilirse gösterilecek mesaj
    String getViolationMessage();
}