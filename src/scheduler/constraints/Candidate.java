package scheduler.constraints;

import scheduler.model.Classroom;
import scheduler.model.Timeslot;

import java.util.List;

public class Candidate {
    public final String courseId;
    public final Timeslot timeslot;
    public final List<Classroom> classrooms; // paralel yürütülecek sınıflar

    public Candidate(String courseId, Timeslot timeslot, List<Classroom> classrooms) {
        this.courseId = courseId;
        this.timeslot = timeslot;
        this.classrooms = classrooms;
    }
}//aaaaa