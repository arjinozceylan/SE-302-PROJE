package scheduler.core;

import scheduler.model.Enrollment;
import java.util.*;

public class ConflictGraphBuilder {

    // courseId -> öğrenciler kümesi
    public Map<String, Set<String>> buildCourseToStudents(List<Enrollment> enrollments) {
        Map<String, Set<String>> map = new HashMap<>();
        for (Enrollment e : enrollments) {
            map.computeIfAbsent(e.getCourseId(), k -> new HashSet<>()).add(e.getStudentId());
        }
        return map;
    }

    // courseId -> derece (kaç dersle öğrenci çakışması var)
    public Map<String, Integer> buildDegrees(Map<String, Set<String>> c2s) {
        Map<String, Integer> deg = new HashMap<>();
        List<String> courses = new ArrayList<>(c2s.keySet());
        for (int i = 0; i < courses.size(); i++) {
            for (int j = i + 1; j < courses.size(); j++) {
                String a = courses.get(i), b = courses.get(j);
                Set<String> sa = c2s.get(a), sb = c2s.get(b);
                if (!Collections.disjoint(sa, sb)) {
                    deg.put(a, deg.getOrDefault(a, 0) + 1);
                    deg.put(b, deg.getOrDefault(b, 0) + 1);
                }
            }
        }
        // kaydı olmayan dersler 0 derece
        for (String c : courses) deg.putIfAbsent(c, 0);
        return deg;
    }
}