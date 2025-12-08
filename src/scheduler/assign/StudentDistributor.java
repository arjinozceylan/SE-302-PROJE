package scheduler.assign;

import scheduler.model.Classroom;
import scheduler.model.StudentExam;
import scheduler.model.Timeslot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Bir dersin öğrencilerini, verilen sınıflara kapasite sınırları ile
 * deterministik olarak dağıtır ve koltuk/sıra numarası atar.
 */
public class StudentDistributor {
    long seed = 42L;

    /**
     * @param courseId Ders kimliği
     * @param timeslot Sınav zaman aralığı
     * @param rooms    Paralel kullanılacak sınıflar (aynı anda)
     * @param students Bu dersi alan öğrencilerin ID listesi
     * @param seed     Deterministik karıştırma için tohum (ör. 42)
     * @return StudentExam kayıtları (öğrenci, sınıf, seatNo ile)
     */
    public List<StudentExam> assign(String courseId,
            Timeslot timeslot,
            List<Classroom> rooms,
            List<String> students,
            long seed) {

        List<String> pool = new ArrayList<>(students);
        // Her sınav için aynı sonuç: courseId ve saatle karıştırılmış deterministik
        // seed
        long s = seed ^ courseId.hashCode() ^ timeslot.getStart().toSecondOfDay();
        Collections.shuffle(pool, new Random(s));

        List<StudentExam> out = new ArrayList<>();
        int index = 0;

        for (Classroom room : rooms) {
            int cap = Math.max(0, room.getCapacity());
            int seatNo = 1;

            for (int k = 0; k < cap && index < pool.size(); k++) {
                String sid = pool.get(index++);
                out.add(new StudentExam(sid, courseId, timeslot, room.getId(), seatNo++));
            }
            if (index >= pool.size())
                break; // tüm öğrenciler yerleşti
        }

        // Not: Eğer toplam kapasite yetersizse, bazı öğrenciler yerleşmemiş olabilir.
        // Bu durumda out.size() < students.size() olur. Çağıran katmanda kontrol edilip
        // raporlanmalı.
        return out;
    }
}