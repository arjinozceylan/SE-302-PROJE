package scheduler.util;

import java.util.Random;

public class RandomUtil {
    private static final Random RNG = new Random(42);

    public static Random getRng() {
        return RNG;
    }
}