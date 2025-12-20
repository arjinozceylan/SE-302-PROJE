package scheduler.model;

public class Student {
    private final String id;
    private final String name; // Yeni eklenen alan

    public Student(String id) {
        this(id, ""); // İsim yoksa boş bırak
    }

    public Student(String id, String name) {
        this.id = id;
        this.name = (name == null) ? "" : name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return id + (name.isEmpty() ? "" : " (" + name + ")");
    }
}