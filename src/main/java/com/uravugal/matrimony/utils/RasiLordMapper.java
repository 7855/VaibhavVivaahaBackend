package com.uravugal.matrimony.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.uravugal.matrimony.enums.Planet;

public final class RasiLordMapper {
    private static final Map<String, Planet> rasiToLord = new HashMap<>();
    static {
        rasiToLord.put("mesham", Planet.MARS);    rasiToLord.put("aries", Planet.MARS);
        rasiToLord.put("rishabam", Planet.VENUS); rasiToLord.put("taurus", Planet.VENUS);
        rasiToLord.put("mithunam", Planet.MERCURY); rasiToLord.put("gemini", Planet.MERCURY);
        rasiToLord.put("katagam", Planet.MOON);   rasiToLord.put("cancer", Planet.MOON);
        rasiToLord.put("simham", Planet.SUN);     rasiToLord.put("leo", Planet.SUN);
        rasiToLord.put("kanni", Planet.MERCURY);  rasiToLord.put("virgo", Planet.MERCURY);
        rasiToLord.put("thulam", Planet.VENUS);   rasiToLord.put("libra", Planet.VENUS);
        rasiToLord.put("vrischikam", Planet.MARS); rasiToLord.put("scorpio", Planet.MARS);
        rasiToLord.put("dhanusu", Planet.JUPITER); rasiToLord.put("sagittarius", Planet.JUPITER);
        rasiToLord.put("magaram", Planet.SATURN);  rasiToLord.put("capricorn", Planet.SATURN);
        rasiToLord.put("kumbam", Planet.SATURN);   rasiToLord.put("aquarius", Planet.SATURN);
        rasiToLord.put("meenam", Planet.JUPITER);  rasiToLord.put("pisces", Planet.JUPITER);

        // Aliases: StarMatch.tsx's rasiData list ships these alternate Tamil transliterations
        // ("Kadagam", "Simmam", "Viruchagam", "Makaram") for Cancer/Leo/Scorpio/Capricorn instead
        // of the spellings above — without these, ~1/3 of real submissions silently failed to
        // resolve a lord at all (getLordFromRasi returned null, and PoruthamService's own
        // mapRasiNameToIndex had the same gap), zeroing out Rasi Adhipathi/Vasya for those users.
        rasiToLord.put("kadagam", Planet.MOON);
        rasiToLord.put("simmam", Planet.SUN);
        rasiToLord.put("viruchagam", Planet.MARS);
        rasiToLord.put("makaram", Planet.SATURN);
    }

    private RasiLordMapper() {}

    public static Planet getLordFromRasi(String rasi) {
        if (rasi == null) return null;
        return rasiToLord.get(rasi.trim().toLowerCase(Locale.ROOT));
    }
}
