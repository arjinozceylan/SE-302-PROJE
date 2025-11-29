package scheduler.model;

import java.time.LocalDate;
import java.util.List;

public class DayWindow {
    private final LocalDate date;
    private final List<TimeRange> ranges;

    public DayWindow(LocalDate date, List<TimeRange> ranges) {
        if (date == null || ranges == null || ranges.isEmpty())
            throw new IllegalArgumentException("Geçersiz gün veya aralık listesi");
        this.date = date;
        this.ranges = ranges;//test
    }


    public LocalDate getDate() { return date; }
    public List<TimeRange> getRanges() { return ranges; }
}