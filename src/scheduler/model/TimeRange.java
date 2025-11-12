package scheduler.model;

import java.time.LocalTime;

public class TimeRange {
    private final LocalTime start;
    private final LocalTime end; // [start, end)

    public TimeRange(LocalTime start, LocalTime end) {
        if (start == null || end == null) throw new IllegalArgumentException("null time");
        if (!start.isBefore(end)) throw new IllegalArgumentException("start < end olmalı");
        this.start = start;
        this.end = end;
    }

    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }

    public int lengthMinutes() {
        return (end.toSecondOfDay() - start.toSecondOfDay()) / 60;
    }

    public boolean contains(LocalTime t) {
        return !t.isBefore(start) && t.isBefore(end);
    }
}