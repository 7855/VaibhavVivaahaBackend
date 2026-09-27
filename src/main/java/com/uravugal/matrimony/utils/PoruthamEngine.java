package com.uravugal.matrimony.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.uravugal.matrimony.dtos.MatchResponseDto;
import com.uravugal.matrimony.dtos.PoruthamResultDto;
import com.uravugal.matrimony.enums.Planet;

/**
 * Tamil Nadu 10-porutham (Thirumana Porutham) engine. Pure and static — no Spring, no DB —
 * so it can be run and checked from a plain Java main.
 *
 * Every count is the Tamil way: from the GIRL's star (or rasi) to the BOY's, counting the
 * girl's own star as 1. Each porutham is graded Uthamam (1 point) / Madhimam (½) / Adhamam (0),
 * and the headline is the point total out of 10 — which is what a Tamil family expects to hear —
 * not a weighted percentage.
 *
 * Rajju and Vedhai are disqualifying: when either fails the verdict is NOT RECOMMENDED no matter
 * how many other poruthams pass. Dina, Gana, Yoni, Rasi and Rajju are the five "mukkiya"
 * (essential) poruthams.
 */
public final class PoruthamEngine {

    public static final String UTHAMAM = "UTHAMAM";
    public static final String MADHIMAM = "MADHIMAM";
    public static final String ADHAMAM = "ADHAMAM";

    private PoruthamEngine() {}

    /** Inclusive Tamil count from `from` to `to` over 27 stars (from == to → 1). */
    public static int countStars(int from, int to) { return ((to - from + 27) % 27) + 1; }
    /** Inclusive Tamil count from `from` to `to` over 12 rasis (from == to → 1). */
    public static int countRasis(int from, int to) { return ((to - from + 12) % 12) + 1; }

    private static PoruthamResultDto grade(String key, String name, String grade, String reason, boolean essential) {
        double points = UTHAMAM.equals(grade) ? 1.0 : MADHIMAM.equals(grade) ? 0.5 : 0.0;
        PoruthamResultDto r = new PoruthamResultDto(key, name,
                UTHAMAM.equals(grade) ? "PASS" : MADHIMAM.equals(grade) ? "PARTIAL" : "FAIL",
                reason, 1, points >= 1 ? 1 : 0);
        r.setGrade(grade);
        r.setPoints(points);
        r.setEssential(essential);
        r.setCounted(true);
        return r;
    }

    private static PoruthamResultDto unknown(String key, String name, String reason, boolean essential) {
        PoruthamResultDto r = new PoruthamResultDto(key, name, "UNKNOWN", reason, 1, 0);
        r.setGrade("UNKNOWN"); r.setPoints(0); r.setEssential(essential); r.setCounted(true);
        return r;
    }

    // ───────────────────────── 1. Dina ─────────────────────────
    public static PoruthamResultDto dina(int b, int g) {
        String k = "dina", n = "Dina Porutham";
        if (b <= 0 || g <= 0) return unknown(k, n, "Star missing", true);
        int count = countStars(b, g);
        if (count == 1) {
            if (StarInfo.SAME_STAR_UTHAMAM.contains(b))
                return grade(k, n, UTHAMAM, "Same star (" + StarInfo.starName(b) + ") — accepted for this star", true);
            if (StarInfo.SAME_STAR_MADHIMAM.contains(b))
                return grade(k, n, MADHIMAM, "Same star (" + StarInfo.starName(b) + ") — acceptable, not ideal", true);
            return grade(k, n, ADHAMAM, "Same star (" + StarInfo.starName(b) + ") is not favourable", true);
        }
        int rem = count % 9;
        boolean good = rem == 0 || rem == 2 || rem == 4 || rem == 6 || rem == 8;
        String why = "Boy's star is " + ordinal(count) + " from the girl's star";
        return grade(k, n, good ? UTHAMAM : ADHAMAM, why + (good ? " — favourable" : " — not favourable"), true);
    }

    // ───────────────────────── 2. Gana ─────────────────────────
    public static PoruthamResultDto gana(int b, int g) {
        String k = "gana", n = "Gana Porutham";
        if (b <= 0 || g <= 0) return unknown(k, n, "Star missing", true);
        String bg = StarInfo.GANA.get(b), gg = StarInfo.GANA.get(g);
        String pair = "Girl " + bg + " · Boy " + gg;
        if (bg.equals(gg)) return grade(k, n, UTHAMAM, pair + " — same gana", true);
        if (bg.equals("Deva") && gg.equals("Manushya")) return grade(k, n, UTHAMAM, pair + " — compatible", true);
        if (bg.equals("Manushya") && gg.equals("Deva")) return grade(k, n, MADHIMAM, pair + " — acceptable", true);
        if (bg.equals("Manushya") && gg.equals("Rakshasa")) return grade(k, n, MADHIMAM, pair + " — acceptable", true);
        return grade(k, n, ADHAMAM, pair + " — temperaments clash", true);
    }

    // ───────────────────────── 3. Mahendra ─────────────────────────
    private static final Set<Integer> MAHENDRA_COUNTS = Set.of(4, 7, 10, 13, 16, 19, 22, 25);
    public static PoruthamResultDto mahendra(int b, int g) {
        String k = "mahendra", n = "Mahendra Porutham";
        if (b <= 0 || g <= 0) return unknown(k, n, "Star missing", false);
        int count = countStars(b, g);
        boolean good = MAHENDRA_COUNTS.contains(count);
        return grade(k, n, good ? UTHAMAM : ADHAMAM,
                "Boy's star is " + ordinal(count) + " from the girl's star" + (good ? " — Mahendra position" : " — not a Mahendra position"), false);
    }

    // ───────────────────────── 4. Stree Dheerga ─────────────────────────
    public static PoruthamResultDto streeDheerga(int b, int g) {
        String k = "streedhirga", n = "Stree Dheerga Porutham";
        if (b <= 0 || g <= 0) return unknown(k, n, "Star missing", false);
        int count = countStars(b, g);
        String why = "Boy's star is " + ordinal(count) + " from the girl's star";
        if (count > 13) return grade(k, n, UTHAMAM, why + " — more than 13, favourable", false);
        if (count > 7) return grade(k, n, MADHIMAM, why + " — acceptable", false);
        return grade(k, n, ADHAMAM, why + " — too close", false);
    }

    // ───────────────────────── 5. Yoni ─────────────────────────
    public static PoruthamResultDto yoni(int b, int g) {
        String k = "yoni", n = "Yoni Porutham";
        if (b <= 0 || g <= 0) return unknown(k, n, "Star missing", true);
        String by = StarInfo.YONI.get(b), gy = StarInfo.YONI.get(g);
        String pair = "Girl " + by + " · Boy " + gy;
        if (gy.equals(StarInfo.YONI_ENEMY.get(by))) return grade(k, n, ADHAMAM, pair + " — enemy animals", true);
        if (by.equals(gy)) {
            boolean oppositeSex = !StarInfo.YONI_MALE.get(b).equals(StarInfo.YONI_MALE.get(g));
            return grade(k, n, oppositeSex ? UTHAMAM : MADHIMAM,
                    pair + (oppositeSex ? " — same animal, ideal" : " — same animal, same sex"), true);
        }
        return grade(k, n, UTHAMAM, pair + " — friendly animals", true);
    }

    // ───────────────────────── 6. Rasi ─────────────────────────
    public static PoruthamResultDto rasi(int b, int g) {
        String k = "rasi", n = "Rasi Porutham";
        if (b <= 0 || g <= 0) return unknown(k, n, "Rasi missing", true);
        int count = countRasis(b, g);
        if (count == 1) return grade(k, n, UTHAMAM, "Same rasi (" + StarInfo.rasiName(b) + ")", true);
        String why = "Boy's rasi is " + ordinal(count) + " from the girl's rasi";
        if (count >= 7 && count != 8) return grade(k, n, UTHAMAM, why + " — favourable", true);
        if (count == 3 || count == 4) return grade(k, n, MADHIMAM, why + " — acceptable", true);
        String label = count == 8 ? " — 6/8 (Shashtashtaka), not favourable"
                : count == 6 ? " — 6/8 (Shashtashtaka), not favourable" : " — not favourable";
        return grade(k, n, ADHAMAM, why + label, true);
    }

    // ───────────────────────── 7. Rasi Adhipathi ─────────────────────────
    private static final Map<Planet, Set<Planet>> FRIENDS = new HashMap<>();
    private static final Map<Planet, Set<Planet>> ENEMIES = new HashMap<>();
    static {
        FRIENDS.put(Planet.SUN, Set.of(Planet.MOON, Planet.MARS, Planet.JUPITER));
        FRIENDS.put(Planet.MOON, Set.of(Planet.SUN, Planet.MERCURY));
        FRIENDS.put(Planet.MARS, Set.of(Planet.SUN, Planet.MOON, Planet.JUPITER));
        FRIENDS.put(Planet.MERCURY, Set.of(Planet.SUN, Planet.VENUS));
        FRIENDS.put(Planet.JUPITER, Set.of(Planet.SUN, Planet.MOON, Planet.MARS));
        FRIENDS.put(Planet.VENUS, Set.of(Planet.MERCURY, Planet.SATURN));
        FRIENDS.put(Planet.SATURN, Set.of(Planet.MERCURY, Planet.VENUS));
        ENEMIES.put(Planet.SUN, Set.of(Planet.VENUS, Planet.SATURN));
        ENEMIES.put(Planet.MOON, Set.of());
        ENEMIES.put(Planet.MARS, Set.of(Planet.MERCURY));
        ENEMIES.put(Planet.MERCURY, Set.of(Planet.MOON));
        ENEMIES.put(Planet.JUPITER, Set.of(Planet.MERCURY, Planet.VENUS));
        ENEMIES.put(Planet.VENUS, Set.of(Planet.SUN, Planet.MOON));
        ENEMIES.put(Planet.SATURN, Set.of(Planet.SUN, Planet.MOON, Planet.MARS));
    }
    private static final Planet[] RASI_LORD = {
        Planet.MARS, Planet.VENUS, Planet.MERCURY, Planet.MOON, Planet.SUN, Planet.MERCURY,
        Planet.VENUS, Planet.MARS, Planet.JUPITER, Planet.SATURN, Planet.SATURN, Planet.JUPITER
    };
    public static Planet lordOf(int rasi) { return rasi >= 1 && rasi <= 12 ? RASI_LORD[rasi - 1] : null; }

    public static PoruthamResultDto rasiAdhipathi(int b, int g) {
        String k = "rasi_adhipathi", n = "Rasi Adhipathi Porutham";
        if (b <= 0 || g <= 0) return unknown(k, n, "Rasi missing", false);
        Planet bl = lordOf(b), gl = lordOf(g);
        String pair = "Lords " + cap(bl) + " and " + cap(gl);
        if (bl == gl) return grade(k, n, UTHAMAM, pair + " — same lord", false);
        boolean bFriend = FRIENDS.get(bl).contains(gl), gFriend = FRIENDS.get(gl).contains(bl);
        boolean bEnemy = ENEMIES.get(bl).contains(gl), gEnemy = ENEMIES.get(gl).contains(bl);
        if (bFriend && gFriend) return grade(k, n, UTHAMAM, pair + " are mutual friends", false);
        if (bEnemy || gEnemy) return grade(k, n, ADHAMAM, pair + " are enemies", false);
        return grade(k, n, MADHIMAM, pair + " are neutral", false);
    }

    // ───────────────────────── 8. Vasya ─────────────────────────
    public static PoruthamResultDto vasya(int b, int g) {
        String k = "vasya", n = "Vasiya Porutham";
        if (b <= 0 || g <= 0) return unknown(k, n, "Rasi missing", false);
        String pair = "Girl " + StarInfo.rasiName(b) + " · Boy " + StarInfo.rasiName(g);
        if (StarInfo.VASYA.get(b).contains(g)) return grade(k, n, UTHAMAM, pair + " — boy's rasi is vasya to the girl's", false);
        if (StarInfo.VASYA.get(g).contains(b)) return grade(k, n, MADHIMAM, pair + " — girl's rasi is vasya to the boy's", false);
        if (b == g) return grade(k, n, MADHIMAM, pair + " — same rasi", false);
        return grade(k, n, ADHAMAM, pair + " — no mutual attraction", false);
    }

    // ───────────────────────── 9. Rajju ─────────────────────────
    public static PoruthamResultDto rajju(int b, int g) {
        String k = "rajju", n = "Rajju Porutham";
        if (b <= 0 || g <= 0) return unknown(k, n, "Star missing", true);
        int br = StarInfo.RAJJU.get(b), gr = StarInfo.RAJJU.get(g);
        if (br != gr)
            return grade(k, n, UTHAMAM, "Girl " + StarInfo.RAJJU_NAME.get(br) + " · Boy " + StarInfo.RAJJU_NAME.get(gr) + " — different rajju", true);
        if (b == g && StarInfo.SAME_STAR_UTHAMAM.contains(b))
            return grade(k, n, MADHIMAM, "Same star (" + StarInfo.starName(b) + ") — Rajju dosham is not counted for this star", true);
        return grade(k, n, ADHAMAM, "Both in " + StarInfo.RAJJU_NAME.get(br) + " rajju — Rajju dosham", true);
    }

    // ───────────────────────── 10. Vedhai ─────────────────────────
    public static PoruthamResultDto vedhai(int b, int g) {
        String k = "vedhai", n = "Vedhai Porutham";
        if (b <= 0 || g <= 0) return unknown(k, n, "Star missing", false);
        boolean afflicted = StarInfo.VEDHA.get(b).contains(g);
        return grade(k, n, afflicted ? ADHAMAM : UTHAMAM,
                afflicted ? StarInfo.starName(b) + " and " + StarInfo.starName(g) + " are Vedhai stars — Vedhai dosham"
                          : "No Vedhai between " + StarInfo.starName(b) + " and " + StarInfo.starName(g), false);
    }

    // ───────────────────────── extra: Nadi (not counted) ─────────────────────────
    public static PoruthamResultDto nadi(int b, int g) {
        String k = "nadi", n = "Nadi (additional)";
        PoruthamResultDto r;
        if (b <= 0 || g <= 0) r = unknown(k, n, "Star missing", false);
        else {
            int bn = StarInfo.NADI.get(b), gn = StarInfo.NADI.get(g);
            String[] names = {"", "Aadi", "Madhya", "Antya"};
            r = grade(k, n, bn != gn ? UTHAMAM : ADHAMAM,
                    "Girl " + names[bn] + " · Boy " + names[gn] + (bn != gn ? " — different nadi" : " — same nadi (Nadi dosham)"), false);
        }
        r.setCounted(false);
        return r;
    }

    /** Full evaluation. Star ids 1..27, rasi ids 1..12; 0 = unknown. */
    public static MatchResponseDto evaluate(int brideStar, int groomStar, int brideRasi, int groomRasi) {
        List<PoruthamResultDto> results = new ArrayList<>();
        PoruthamResultDto dina = dina(brideStar, groomStar);        results.add(dina);
        PoruthamResultDto gana = gana(brideStar, groomStar);        results.add(gana);
        PoruthamResultDto mahendra = mahendra(brideStar, groomStar); results.add(mahendra);
        PoruthamResultDto stree = streeDheerga(brideStar, groomStar); results.add(stree);
        PoruthamResultDto yoni = yoni(brideStar, groomStar);        results.add(yoni);
        PoruthamResultDto rasi = rasi(brideRasi, groomRasi);        results.add(rasi);
        PoruthamResultDto adhipathi = rasiAdhipathi(brideRasi, groomRasi); results.add(adhipathi);
        PoruthamResultDto vasya = vasya(brideRasi, groomRasi);      results.add(vasya);
        PoruthamResultDto rajju = rajju(brideStar, groomStar);      results.add(rajju);
        PoruthamResultDto vedhai = vedhai(brideStar, groomStar);    results.add(vedhai);

        double points = 0; int u = 0, m = 0, a = 0, essentialsPassed = 0, essentialsTotal = 0;
        for (PoruthamResultDto r : results) {
            points += r.getPoints();
            if (UTHAMAM.equals(r.getGrade())) u++;
            else if (MADHIMAM.equals(r.getGrade())) m++;
            else a++;
            if (r.isEssential()) { essentialsTotal++; if (r.getPoints() > 0) essentialsPassed++; }
        }

        List<String> doshams = new ArrayList<>();
        if (ADHAMAM.equals(rajju.getGrade())) doshams.add("Rajju Dosham");
        if (ADHAMAM.equals(vedhai.getGrade())) doshams.add("Vedhai Dosham");

        String verdict, note;
        if (!doshams.isEmpty()) {
            verdict = "NOT RECOMMENDED";
            note = String.join(" and ", doshams) + " — traditionally a match is not made whatever the other poruthams say.";
        } else if (points >= 7.5 && essentialsPassed == essentialsTotal) {
            verdict = "EXCELLENT MATCH";
            note = "All five essential poruthams agree and " + fmt(points) + " of 10 poruthams are present.";
        } else if (points >= 5 && essentialsPassed == essentialsTotal) {
            verdict = "GOOD MATCH";
            note = fmt(points) + " of 10 poruthams are present, including all five essential ones.";
        } else if (points >= 5) {
            verdict = "FAIR MATCH";
            note = fmt(points) + " of 10 poruthams are present, but " + (essentialsTotal - essentialsPassed)
                    + " essential porutham" + (essentialsTotal - essentialsPassed == 1 ? "" : "s") + " missing — consult an astrologer.";
        } else {
            verdict = "NOT RECOMMENDED";
            note = "Only " + fmt(points) + " of 10 poruthams are present; at least 5 are expected.";
        }

        List<PoruthamResultDto> extras = new ArrayList<>();
        extras.add(nadi(brideStar, groomStar));

        int percent = (int) Math.round(points * 10);
        MatchResponseDto out = new MatchResponseDto(percent, percent, verdict, 100, results);
        out.setPoruthamPoints(points);
        out.setTotalPoruthams(10);
        out.setUthamamCount(u);
        out.setMadhimamCount(m);
        out.setAdhamamCount(a);
        out.setEssentialsPassed(essentialsPassed);
        out.setEssentialsTotal(essentialsTotal);
        out.setDoshams(doshams);
        out.setVerdictNote(note);
        out.setExtras(extras);
        out.setBrideStar(StarInfo.starName(brideStar));
        out.setGroomStar(StarInfo.starName(groomStar));
        out.setBrideRasi(StarInfo.rasiName(brideRasi));
        out.setGroomRasi(StarInfo.rasiName(groomRasi));
        return out;
    }

    private static String fmt(double d) { return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d); }
    private static String cap(Planet p) { String s = p.name().toLowerCase(); return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private static String ordinal(int n) {
        if (n % 100 >= 11 && n % 100 <= 13) return n + "th";
        switch (n % 10) { case 1: return n + "st"; case 2: return n + "nd"; case 3: return n + "rd"; default: return n + "th"; }
    }
}
