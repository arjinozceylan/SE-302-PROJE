package scheduler.model;

public class Course {
    private final String id;
    private int durationMinutes;
    private boolean ignored = false;

    private int minRoomCapacity = 0;
    private int maxRoomCapacity;

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

    public void setMaxRoomCapacity(int maxRoomCapacity) {
        this.maxRoomCapacity = maxRoomCapacity;
    }

    public int getMaxRoomCapacity() {
        return maxRoomCapacity;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    @Override
    public String toString() {
        return id;
    }
}