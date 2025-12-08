package scheduler.model;

public class Course {
    private final String id;
    private final int durationMinutes;

    public Course(String id, int durationMinutes) {
        this.id = id;
        this.durationMinutes = durationMinutes;
    }

    public String getId() {
        return id;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }
}
