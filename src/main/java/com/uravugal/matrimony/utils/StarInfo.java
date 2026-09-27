package com.uravugal.matrimony.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reference tables for the Tamil Nadu 10-porutham (Thirumana Porutham) system.
 *
 * Star numbering is the Tamil order 1..27 (Aswini .. Revathi) — the same order as the `star`
 * keyValue row and the app's pickers. Rasi numbering is 1..12 (Mesham .. Meenam).
 *
 * Sources (2026-09-27): findyourfate.com/indianastro/10porutham.htm, hindu-blog.com (rajju),
 * epanchang.com (vasya, vedha), astrologyatoz (dina/stree dheerga/rasi grading),
 * dheivegam.com, priestservices.com. Where sources disagreed the classical (Tamil almanac)
 * form was taken and the choice is noted next to the table.
 */
public final class StarInfo {

    /** Canonical Tamil star names in order, index 0 = star 1 (Aswini). */
    public static final List<String> STARS = List.of(
            "Aswini", "Bharani", "Krithikai", "Rohini", "Mirugasiridham", "Thiruvathirai",
            "Punarpusam", "Poosam", "Ayilyam", "Magam", "Pooram", "Uthiram",
            "Hastham", "Chithirai", "Swathi", "Visagam", "Anusham", "Kettai",
            "Moolam", "Pooradam", "Uthiradam", "Thiruvonam", "Avittam", "Sathayam",
            "Poorattathi", "Uthirattathi", "Revathi");

    /** Canonical Tamil rasi names in order, index 0 = rasi 1 (Mesham). */
    public static final List<String> RASIS = List.of(
            "Mesham", "Rishabam", "Mithunam", "Kadagam", "Simmam", "Kanni",
            "Thulam", "Viruchigam", "Dhanusu", "Magaram", "Kumbam", "Meenam");

    /** Every spelling of a star the app, the DB vocabulary or a member could send → 1..27. */
    private static final Map<String, Integer> STAR_ALIASES = new HashMap<>();
    /** Every spelling of a rasi the app, the edit page or a member could send → 1..12. */
    private static final Map<String, Integer> RASI_ALIASES = new HashMap<>();

    public static final Map<Integer, String> GANA = new HashMap<>();
    public static final Map<Integer, String> YONI = new HashMap<>();
    /** true = male yoni, false = female yoni (each animal has one star of each sex). */
    public static final Map<Integer, Boolean> YONI_MALE = new HashMap<>();
    public static final Map<String, String> YONI_ENEMY = new HashMap<>();
    /** 1 = Pada (foot), 2 = Kati (thigh/waist), 3 = Nabhi (navel), 4 = Kanta (neck), 5 = Siro (head). */
    public static final Map<Integer, Integer> RAJJU = new HashMap<>();
    public static final Map<Integer, String> RAJJU_NAME = Map.of(
            1, "Pada (foot)", 2, "Kati (waist)", 3, "Nabhi (navel)", 4, "Kanta (neck)", 5, "Siro (head)");
    /** Vedhai: star → set of stars that afflict it. */
    public static final Map<Integer, Set<Integer>> VEDHA = new HashMap<>();
    /** Nadi (Aadi/Madhya/Antya). Not one of the 10 Tamil poruthams — reported as extra information only. */
    public static final Map<Integer, Integer> NADI = new HashMap<>();
    /** Vasya: girl's rasi → the rasis attracted to it (classical table, epanchang.com). */
    public static final Map<Integer, Set<Integer>> VASYA = new HashMap<>();

    /**
     * Same star for both: the almanacs accept it outright for these stars (Dina Uthamam,
     * and Rajju is not treated as a dosham for them).
     */
    public static final Set<Integer> SAME_STAR_UTHAMAM = Set.of(4, 6, 8, 10, 13, 16, 21, 22, 27);
    /** Same star for both: accepted as Madhimam for these stars. */
    public static final Set<Integer> SAME_STAR_MADHIMAM = Set.of(1, 2, 3, 5, 7, 12, 14, 17);

    static {
        for (int i = 0; i < STARS.size(); i++) STAR_ALIASES.put(key(STARS.get(i)), i + 1);
        // Spellings seen in older rows / other transliterations.
        alias(STAR_ALIASES, 1, "ashwini", "aswathi", "ashvini");
        alias(STAR_ALIASES, 3, "karthigai", "kiruthigai", "krittika", "karthikai");
        alias(STAR_ALIASES, 5, "mirugashirisham", "mirugasritam", "mrigashira", "mrigasira", "mirugaseersham");
        alias(STAR_ALIASES, 6, "thiruvadhirai", "ardra", "arudra", "thiruvathira");
        alias(STAR_ALIASES, 7, "punarpoosam", "punarvasu");
        alias(STAR_ALIASES, 8, "pusam", "pushya", "poosam");
        alias(STAR_ALIASES, 9, "ayilam", "ashlesha", "aslesha");
        alias(STAR_ALIASES, 10, "maham", "magha");
        alias(STAR_ALIASES, 11, "puram", "purva phalguni");
        alias(STAR_ALIASES, 12, "uthram", "uttara phalguni");
        alias(STAR_ALIASES, 13, "hasta", "astham");
        alias(STAR_ALIASES, 14, "chitra", "chithra", "chitrai");
        alias(STAR_ALIASES, 15, "swati", "swathy");
        alias(STAR_ALIASES, 16, "visakam", "vishaka", "visakha");
        alias(STAR_ALIASES, 17, "anuradha", "anusam");
        alias(STAR_ALIASES, 18, "kettai", "jyeshta", "jyeshtha", "kettai");
        alias(STAR_ALIASES, 19, "moola", "mula");
        alias(STAR_ALIASES, 20, "puradam", "purvashada");
        alias(STAR_ALIASES, 21, "uthradam", "uttarashada");
        alias(STAR_ALIASES, 22, "thiruvonam", "sravana", "shravana");
        alias(STAR_ALIASES, 23, "avitam", "dhanishta");
        alias(STAR_ALIASES, 24, "sadayam", "shatabhisha", "satabisha");
        alias(STAR_ALIASES, 25, "puratathi", "purva bhadrapada");
        alias(STAR_ALIASES, 26, "uttaratathi", "uthirattadhi", "uttara bhadrapada");
        alias(STAR_ALIASES, 27, "revati");

        for (int i = 0; i < RASIS.size(); i++) RASI_ALIASES.put(key(RASIS.get(i)), i + 1);
        alias(RASI_ALIASES, 1, "mesha", "aries");
        alias(RASI_ALIASES, 2, "rishabha", "vrishabha", "taurus");
        alias(RASI_ALIASES, 3, "mithuna", "gemini");
        alias(RASI_ALIASES, 4, "katagam", "kataka", "karka", "cancer");
        alias(RASI_ALIASES, 5, "simham", "simha", "leo");
        alias(RASI_ALIASES, 6, "kanya", "virgo");
        alias(RASI_ALIASES, 7, "thula", "tula", "libra");
        // The edit page stores "Vrichigam"; the old Star Match list sent "Viruchagam".
        alias(RASI_ALIASES, 8, "vrichigam", "viruchagam", "vrischikam", "vrischika", "scorpio");
        alias(RASI_ALIASES, 9, "dhanushu", "dhanus", "dhanu", "sagittarius");
        alias(RASI_ALIASES, 10, "makaram", "makara", "capricorn");
        alias(RASI_ALIASES, 11, "kumba", "kumbha", "aquarius");
        alias(RASI_ALIASES, 12, "meena", "pisces");

        // ---------- GANA (standard 9 / 9 / 9 split) ----------
        for (int s : new int[]{1, 5, 7, 8, 13, 15, 17, 22, 27}) GANA.put(s, "Deva");
        for (int s : new int[]{2, 4, 6, 11, 12, 20, 21, 25, 26}) GANA.put(s, "Manushya");
        for (int s : new int[]{3, 9, 10, 14, 16, 18, 19, 23, 24}) GANA.put(s, "Rakshasa");

        // ---------- YONI (14 animals, one male + one female star each) ----------
        yoni(1, "Horse", true);      yoni(24, "Horse", false);
        yoni(2, "Elephant", true);   yoni(27, "Elephant", false);
        yoni(8, "Goat", true);       yoni(3, "Goat", false);
        yoni(4, "Snake", true);      yoni(5, "Snake", false);
        yoni(19, "Dog", true);       yoni(6, "Dog", false);
        yoni(9, "Cat", true);        yoni(7, "Cat", false);
        yoni(10, "Rat", true);       yoni(11, "Rat", false);
        yoni(12, "Cow", true);       yoni(26, "Cow", false);
        yoni(15, "Buffalo", true);   yoni(13, "Buffalo", false);
        yoni(16, "Tiger", true);     yoni(14, "Tiger", false);
        yoni(18, "Deer", true);      yoni(17, "Deer", false);
        yoni(20, "Monkey", true);    yoni(22, "Monkey", false);
        yoni(25, "Lion", true);      yoni(23, "Lion", false);
        yoni(21, "Mongoose", false); // Uthiradam is the only mongoose star

        enemy("Horse", "Buffalo");
        enemy("Elephant", "Lion");
        enemy("Goat", "Monkey");
        enemy("Snake", "Mongoose");
        enemy("Dog", "Deer");
        enemy("Cat", "Rat");
        enemy("Cow", "Tiger");

        // ---------- RAJJU (five body zones) ----------
        for (int s : new int[]{1, 9, 10, 18, 19, 27}) RAJJU.put(s, 1);  // Pada
        for (int s : new int[]{2, 8, 11, 17, 20, 26}) RAJJU.put(s, 2);  // Kati
        for (int s : new int[]{3, 7, 12, 16, 21, 25}) RAJJU.put(s, 3);  // Nabhi
        for (int s : new int[]{4, 6, 13, 15, 22, 24}) RAJJU.put(s, 4);  // Kanta
        for (int s : new int[]{5, 14, 23}) RAJJU.put(s, 5);             // Siro

        // ---------- VEDHAI (afflicting pairs; Mirugasiridham / Chithirai / Avittam are mutual) ----------
        int[][] pairs = {
            {1, 18}, {2, 17}, {3, 16}, {4, 15}, {6, 22}, {7, 21}, {8, 20}, {9, 19},
            {10, 27}, {11, 26}, {12, 25}, {13, 24}, {5, 14}, {5, 23}, {14, 23}
        };
        for (int s = 1; s <= 27; s++) VEDHA.put(s, new java.util.HashSet<>());
        for (int[] p : pairs) { VEDHA.get(p[0]).add(p[1]); VEDHA.get(p[1]).add(p[0]); }

        // ---------- NADI (extra info only) ----------
        int[] nadiCycle = {1, 2, 3, 3, 2, 1};
        for (int i = 1; i <= 27; i++) NADI.put(i, nadiCycle[(i - 1) % 6]);

        // ---------- VASYA (classical table: rasi → rasis it attracts) ----------
        vasya(1, 5, 8);      // Mesham → Simmam, Viruchigam
        vasya(2, 4, 7);      // Rishabam → Kadagam, Thulam
        vasya(3, 6);         // Mithunam → Kanni
        vasya(4, 8, 9);      // Kadagam → Viruchigam, Dhanusu
        vasya(5, 7);         // Simmam → Thulam
        vasya(6, 3, 12);     // Kanni → Mithunam, Meenam
        vasya(7, 10);        // Thulam → Magaram
        vasya(8, 4, 6);      // Viruchigam → Kadagam, Kanni
        vasya(9, 12);        // Dhanusu → Meenam
        vasya(10, 1, 11);    // Magaram → Mesham, Kumbam
        vasya(11, 1);        // Kumbam → Mesham
        vasya(12, 10);       // Meenam → Magaram
    }

    private static String key(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }
    private static void alias(Map<String, Integer> map, int id, String... names) {
        for (String n : names) map.put(key(n), id);
    }
    private static void yoni(int star, String animal, boolean male) { YONI.put(star, animal); YONI_MALE.put(star, male); }
    private static void enemy(String a, String b) { YONI_ENEMY.put(a, b); YONI_ENEMY.put(b, a); }
    private static void vasya(int rasi, int... attracted) {
        Set<Integer> s = new java.util.HashSet<>();
        for (int a : attracted) s.add(a);
        VASYA.put(rasi, Collections.unmodifiableSet(s));
    }

    /** Resolve any spelling to 1..27, or 0 when unknown. */
    public static int starId(String name) { return STAR_ALIASES.getOrDefault(key(name), 0); }
    /** Resolve any spelling to 1..12, or 0 when unknown. */
    public static int rasiId(String name) { return RASI_ALIASES.getOrDefault(key(name), 0); }
    public static String starName(int id) { return id >= 1 && id <= 27 ? STARS.get(id - 1) : "?"; }
    public static String rasiName(int id) { return id >= 1 && id <= 12 ? RASIS.get(id - 1) : "?"; }

    private StarInfo() {}
}
