package com.uravugal.matrimony.utils;

import java.util.HashMap;
import java.util.Map;

public final class StarInfo {

    public static final Map<Integer, String> GANA = new HashMap<>();
    public static final Map<Integer, String> YONI = new HashMap<>();
    public static final Map<Integer, Integer> RAJJU = new HashMap<>();
    public static final Map<Integer, Integer> NADI = new HashMap<>();

    static {

        // ---------- GANA ----------
        GANA.put(1,"Deva"); GANA.put(2,"Manushya"); GANA.put(3,"Rakshasa");
        GANA.put(4,"Deva"); GANA.put(5,"Deva"); GANA.put(6,"Rakshasa");
        GANA.put(7,"Deva"); GANA.put(8,"Deva"); GANA.put(9,"Rakshasa");
        GANA.put(10,"Manushya"); GANA.put(11,"Manushya"); GANA.put(12,"Manushya");
        GANA.put(13,"Deva"); GANA.put(14,"Rakshasa"); GANA.put(15,"Deva");
        GANA.put(16,"Rakshasa"); GANA.put(17,"Deva"); GANA.put(18,"Rakshasa");
        GANA.put(19,"Rakshasa"); GANA.put(20,"Manushya"); GANA.put(21,"Manushya");
        GANA.put(22,"Deva"); GANA.put(23,"Rakshasa"); GANA.put(24,"Rakshasa");
        GANA.put(25,"Manushya"); GANA.put(26,"Manushya"); GANA.put(27,"Deva");

        // ---------- YONI ----------
        YONI.put(1,"Horse"); YONI.put(2,"Elephant"); YONI.put(3,"Sheep");
        YONI.put(4,"Serpent"); YONI.put(5,"Dog"); YONI.put(6,"Cat");
        YONI.put(7,"Rat"); YONI.put(8,"Cow"); YONI.put(9,"Buffalo");
        YONI.put(10,"Lion"); YONI.put(11,"Tiger"); YONI.put(12,"Deer");
        YONI.put(13,"Monkey"); YONI.put(14,"Crow"); YONI.put(15,"Eagle");
        YONI.put(16,"Goat"); YONI.put(17,"Pig"); YONI.put(18,"Mongoose");
        YONI.put(19,"Horse"); YONI.put(20,"Elephant"); YONI.put(21,"Sheep");
        YONI.put(22,"Serpent"); YONI.put(23,"Dog"); YONI.put(24,"Cat");
        YONI.put(25,"Rat"); YONI.put(26,"Cow"); YONI.put(27,"Buffalo");

        // ---------- RAJJU (1..5 groups) ----------
        RAJJU.put(1,5); RAJJU.put(2,4); RAJJU.put(3,3); RAJJU.put(4,2); RAJJU.put(5,1);
        RAJJU.put(6,5); RAJJU.put(7,4); RAJJU.put(8,3); RAJJU.put(9,2); RAJJU.put(10,1);
        RAJJU.put(11,5); RAJJU.put(12,4); RAJJU.put(13,3); RAJJU.put(14,2); RAJJU.put(15,1);
        RAJJU.put(16,5); RAJJU.put(17,4); RAJJU.put(18,3); RAJJU.put(19,2); RAJJU.put(20,1);
        RAJJU.put(21,5); RAJJU.put(22,4); RAJJU.put(23,3); RAJJU.put(24,2); RAJJU.put(25,1);
        RAJJU.put(26,5); RAJJU.put(27,4);

        // ---------- NADI (1,2,3 groups) ----------
       // ---------- NADI (Correct 1 = Adi, 2 = Madhya, 3 = Antya) ----------
NADI.put(1, 1);  // Aswini
NADI.put(2, 1);  // Bharani
NADI.put(3, 1);  // Krittika

// Madhya Nadi
NADI.put(4, 2);  // Rohini
NADI.put(5, 2);  // Mrigashira
NADI.put(6, 2);  // Arudra

// Antya Nadi
for (int i = 7; i <= 27; i++) {
    NADI.put(i, 3);
}
    }

    private StarInfo() {}
}
