package scheduler.model;

import java.util.List;

public class Placement {
    private final String courseId;
    private final Timeslot timeslot;
    private final List<Classroom> classrooms; // paralel yürütülecek sınıflar

    public Placement(String courseId, Timeslot timeslot, List<Classroom> classrooms) {
        this.courseId = courseId;
        this.timeslot = timeslot;
        this.classrooms = classrooms;
    }//aaaaa

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    public String getCourseId() { return courseId; }
    public Timeslot getTimeslot() { return timeslot; }
    public List<Classroom> getClassrooms() { return classrooms; }
}