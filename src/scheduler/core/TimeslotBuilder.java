package scheduler.core;

import scheduler.model.*;
import scheduler.config.SchedulingConfig;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class TimeslotBuilder {

    public List<Timeslot> build(List<DayWindow> dayWindows, int durationMinutes) {
        List<Timeslot> result = new ArrayList<>();

        for (DayWindow dw : dayWindows) {
            for (TimeRange r : dw.getRanges()) {
                LocalTime start = r.getStart();

                while (true) {
                    LocalTime end = start.plusMinutes(durationMinutes);
                    // Başlangıç ve bitiş pencere içinde mi kontrol et
                    if (end.isAfter(r.getEnd())) break;

                    result.add(new Timeslot(dw.getDate(), start, end));
                    // 10 dakikalık adımlarla ilerle
                    start = start.plusMinutes(SchedulingConfig.GRID_MINUTES);
                }
            }
        }
        return result;
    }
}