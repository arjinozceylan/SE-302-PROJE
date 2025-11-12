package scheduler.constraints;

import scheduler.model.Placement;
import java.util.HashMap;
import java.util.Map;

public class PartialSchedule {
    // Yerleştirilen dersler (courseId -> Placement)
    private final Map<String, Placement> placements = new HashMap<>();

    public Map<String, Placement> getPlacements() {
        return placements;
    }

    // Yeni yerleşim ekle
    public void addPlacement(Placement placement) {
        placements.put(placement.getCourseId(), placement);
    }

    // Belirli dersin yerleşimi var mı kontrol et
    public boolean contains(String courseId) {
        return placements.containsKey(courseId);
    }
}