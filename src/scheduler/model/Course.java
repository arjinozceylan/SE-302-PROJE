package scheduler.model;

import java.util.Objects;

public class Course {
    private final String id;
    private int durationMinutes;

    // Kapasite Sınırları (0 = Limit Yok)
    private int minRoomCapacity = 0;
    private int maxRoomCapacity = 0;

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

    public int getMaxRoomCapacity() {
        return maxRoomCapacity;
    }

    public void setMaxRoomCapacity(int maxRoomCapacity) {
        this.maxRoomCapacity = maxRoomCapacity;
    }

    @Override
    public String toString() {
        return id;
    }

    // Listelerin seçimleri hatırlaması için
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Course course = (Course) o;
        return Objects.equals(id, course.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}