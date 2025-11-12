package scheduler.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class Timeslot {
    private final LocalDate date;
    private final LocalTime start;
    private final LocalTime end; // [start, end)

    public Timeslot(LocalDate date, LocalTime start, LocalTime end) {
        if (date == null || start == null || end == null)
            throw new IllegalArgumentException("null zaman");
        if (!start.isBefore(end))
            throw new IllegalArgumentException("start < end olmalı");
        this.date = date;
        this.start = start;
        this.end = end;
    }

    public LocalDate getDate() { return date; }
    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }
}
