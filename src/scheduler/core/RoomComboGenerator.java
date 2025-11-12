package scheduler.core;

import scheduler.model.Classroom;
import java.util.*;

/**
 * Kapasiteyi karşılayan en az sınıf sayılı kombinasyonları üretir.
 * Önce tekli, sonra ikili, sonra üçlü kombinasyonlar.
 */
public class RoomComboGenerator {

    public List<List<Classroom>> generateMinimalCombos(List<Classroom> rooms, int needed, int maxReturn) {
        if (rooms == null || rooms.isEmpty()) return List.of();

        List<Classroom> sorted = new ArrayList<>(rooms);
        sorted.sort(Comparator.comparingInt(Classroom::getCapacity).reversed());

        List<List<Classroom>> result = new ArrayList<>();

        // 1) Tek sınıf
        for (Classroom r : sorted) {
            if (r.getCapacity() >= needed) {
                result.add(List.of(r));
                if (result.size() >= maxReturn) return result;
            }
        }

        // 2) İkili
        for (int i = 0; i < sorted.size(); i++) {
            Classroom a = sorted.get(i);
            for (int j = i + 1; j < sorted.size(); j++) {
                Classroom b = sorted.get(j);
                if (a.getCapacity() + b.getCapacity() >= needed) {
                    result.add(List.of(a, b));
                    if (result.size() >= maxReturn) return result;
                }
            }
        }

        // 3) Üçlü
        for (int i = 0; i < sorted.size(); i++) {
            Classroom a = sorted.get(i);
            for (int j = i + 1; j < sorted.size(); j++) {
                Classroom b = sorted.get(j);
                int capAB = a.getCapacity() + b.getCapacity();
                if (capAB >= needed) continue; // ikili zaten yeterliydi
                for (int k = j + 1; k < sorted.size(); k++) {
                    Classroom c = sorted.get(k);
                    if (capAB + c.getCapacity() >= needed) {
                        result.add(List.of(a, b, c));
                        if (result.size() >= maxReturn) return result;
                    }
                }
            }
        }

        return result;
    }
    public List<Classroom> generateGreedyOrdered(List<Classroom> rooms, int needed, boolean preferLargeFirst) {
        if (rooms == null || rooms.isEmpty()) return List.of();

        List<Classroom> ordered = new ArrayList<>(rooms);
        if (preferLargeFirst) {
            ordered.sort(Comparator.comparingInt(Classroom::getCapacity).reversed());
        } else {
            ordered.sort(Comparator.comparingInt(Classroom::getCapacity));
        }

        List<Classroom> chosen = new ArrayList<>();
        int total = 0;
        for (Classroom r : ordered) {
            chosen.add(r);
            total += r.getCapacity();
            if (total >= needed) break;
        }
        return chosen; // total < needed ise kapasite yetersiz; çağıran tarafta kontrol edeceğiz
    }

    public static int totalCapacity(List<Classroom> rooms) {
        int sum = 0;
        for (Classroom r : rooms) sum += r.getCapacity();
        return sum;
    }
    public static boolean meetsNeed(List<Classroom> chosen, int needed) {
        return totalCapacity(chosen) >= needed;
    }
}