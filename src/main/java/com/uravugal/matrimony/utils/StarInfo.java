package com.uravugal.matrimony.utils;

import java.util.HashMap;
import java.util.Map;

public final class StarInfo {

    public static final Map<Integer, String> GANA = new HashMap<>();
    public static final Map<Integer, String> YONI = new HashMap<>();
    public static final Map<Integer, Integer> RAJJU = new HashMap<>();
    public static final Map<Integer, Integer> NADI = new HashMap<>();
    public static final Map<Integer, Integer> VEDHA = new HashMap<>();
    public static final Map<String, String> YONI_ENEMY = new HashMap<>();

    static {

        // ---------- GANA ----------
        // Deva Gana: Ashwini, Mrigashira, Punarvasu, Pushya, Hasta, Swati, Anuradha, Shravana, Revati
        // Manushya Gana: Bharani, Rohini, Ardra, P.Phalguni, U.Phalguni, P.Ashadha, U.Ashadha, P.Bhadrapada, U.Bhadrapada
        // Rakshasa Gana: Krittika, Ashlesha, Magha, Chitra, Vishakha, Jyeshtha, Moola, Dhanishta, Shatabhisha
        GANA.put(1,"Deva"); GANA.put(2,"Manushya"); GANA.put(3,"Rakshasa");
        GANA.put(4,"Manushya"); GANA.put(5,"Deva"); GANA.put(6,"Manushya");
        GANA.put(7,"Deva"); GANA.put(8,"Deva"); GANA.put(9,"Rakshasa");
        GANA.put(10,"Rakshasa"); GANA.put(11,"Manushya"); GANA.put(12,"Manushya");
        GANA.put(13,"Deva"); GANA.put(14,"Rakshasa"); GANA.put(15,"Deva");
        GANA.put(16,"Rakshasa"); GANA.put(17,"Deva"); GANA.put(18,"Rakshasa");
        GANA.put(19,"Rakshasa"); GANA.put(20,"Manushya"); GANA.put(21,"Manushya");
        GANA.put(22,"Deva"); GANA.put(23,"Rakshasa"); GANA.put(24,"Rakshasa");
        GANA.put(25,"Manushya"); GANA.put(26,"Manushya"); GANA.put(27,"Deva");

        // ---------- YONI ----------
        // Correct per-nakshatra classical assignment (14 distinct yoni animals, each nakshatra
        // has one fixed animal — this is NOT a simple sequential/repeating list of names).
        YONI.put(1,"Horse"); YONI.put(2,"Elephant"); YONI.put(3,"Sheep");
        YONI.put(4,"Serpent"); YONI.put(5,"Serpent"); YONI.put(6,"Dog");
        YONI.put(7,"Cat"); YONI.put(8,"Sheep"); YONI.put(9,"Cat");
        YONI.put(10,"Rat"); YONI.put(11,"Rat"); YONI.put(12,"Cow");
        YONI.put(13,"Buffalo"); YONI.put(14,"Tiger"); YONI.put(15,"Buffalo");
        YONI.put(16,"Tiger"); YONI.put(17,"Deer"); YONI.put(18,"Deer");
        YONI.put(19,"Dog"); YONI.put(20,"Monkey"); YONI.put(21,"Mongoose");
        YONI.put(22,"Monkey"); YONI.put(23,"Lion"); YONI.put(24,"Horse");
        YONI.put(25,"Lion"); YONI.put(26,"Cow"); YONI.put(27,"Elephant");

        // Each of the 14 yoni animals has exactly one natural enemy (Yoni Vaira) — a pair is
        // incompatible only if they land on opposite sides of one of these 7 pairs; every other
        // combination (same animal, or any non-enemy pairing) is fine.
        YONI_ENEMY.put("Horse","Buffalo"); YONI_ENEMY.put("Buffalo","Horse");
        YONI_ENEMY.put("Elephant","Lion"); YONI_ENEMY.put("Lion","Elephant");
        YONI_ENEMY.put("Serpent","Mongoose"); YONI_ENEMY.put("Mongoose","Serpent");
        YONI_ENEMY.put("Dog","Deer"); YONI_ENEMY.put("Deer","Dog");
        YONI_ENEMY.put("Cat","Rat"); YONI_ENEMY.put("Rat","Cat");
        YONI_ENEMY.put("Cow","Tiger"); YONI_ENEMY.put("Tiger","Cow");
        YONI_ENEMY.put("Monkey","Sheep"); YONI_ENEMY.put("Sheep","Monkey");

        // ---------- RAJJU (5 body-part groups) ----------
        // Groups repeat in a mirrored 9-star cycle across all 27 nakshatras:
        // Pada, Kati, Nabhi, Kantha, Shira, Kantha, Nabhi, Kati, Pada — repeated 3 times.
        // Numeric codes below are arbitrary labels (only equality is checked); 1=Pada, 2=Kati,
        // 3=Nabhi, 4=Kantha, 5=Shira.
        int[] rajjuCycle = {1,2,3,4,5,4,3,2,1};
        for (int i = 1; i <= 27; i++) {
            RAJJU.put(i, rajjuCycle[(i - 1) % 9]);
        }

        // ---------- NADI (1 = Adi, 2 = Madhya, 3 = Antya) ----------
        // Repeats in a mirrored 6-star cycle across all 27 nakshatras: Adi, Madhya, Antya,
        // Antya, Madhya, Adi — repeated (partial on the final, 27th star).
        int[] nadiCycle = {1,2,3,3,2,1};
        for (int i = 1; i <= 27; i++) {
            NADI.put(i, nadiCycle[(i - 1) % 6]);
        }

        // ---------- VEDHA (mutual-enemy star pairs) ----------
        // Each nakshatra has at most one Vedha ("affliction") partner; Dhanishta (23) has none.
        int[][] vedhaPairs = {
            {1,18},{2,17},{3,16},{4,15},{5,14},{6,13},{7,12},{8,11},{9,10},
            {19,27},{20,26},{21,25},{22,24}
        };
        for (int[] pair : vedhaPairs) {
            VEDHA.put(pair[0], pair[1]);
            VEDHA.put(pair[1], pair[0]);
        }
    }

    private StarInfo() {}
}
