package scheduler.model;

public class Classroom {
    private final String id;
    private final int capacity;

    public Classroom(String id, int capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    public String getId() {
        return id;
    }

    public int getCapacity() {
        return capacity;
    }
}