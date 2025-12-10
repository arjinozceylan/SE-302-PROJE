package scheduler.model;

public class Course {
    private final String id;
    private int durationMinutes;

    private int minRoomCapacity = 0;

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

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getMinRoomCapacity() {
        return minRoomCapacity;
    }

    public void setMinRoomCapacity(int minRoomCapacity) {
        this.minRoomCapacity = minRoomCapacity;
    }

    @Override
    public String toString() {
        return id;
    }
}