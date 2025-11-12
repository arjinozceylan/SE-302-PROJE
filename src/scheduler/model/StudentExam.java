package scheduler.model;

public class StudentExam {
    private final String studentId;
    private final String courseId;
    private final Timeslot timeslot;
    private final String classroomId;
    private final int seatNo; // 1..capacity

    public StudentExam(String studentId, String courseId,
                       Timeslot timeslot, String classroomId, int seatNo) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.timeslot = timeslot;
        this.classroomId = classroomId;
        this.seatNo = seatNo;
    }

    public String getStudentId() { return studentId; }
    public String getCourseId() { return courseId; }
    public Timeslot getTimeslot() { return timeslot; }
    public String getClassroomId() { return classroomId; }
    public int getSeatNo() { return seatNo; }
}