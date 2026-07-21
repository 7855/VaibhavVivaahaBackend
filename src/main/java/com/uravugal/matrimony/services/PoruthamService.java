package com.uravugal.matrimony.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uravugal.matrimony.dtos.MatchRequestDto;
import com.uravugal.matrimony.dtos.MatchResponseDto;
import com.uravugal.matrimony.dtos.PoruthamResultDto;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.enums.Planet;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.InterestRequest;
import com.uravugal.matrimony.models.KeyValue;
import com.uravugal.matrimony.repositories.InterestRequestRepository;
import com.uravugal.matrimony.repositories.KeyValueRepository;
import com.uravugal.matrimony.utils.StarInfo;
import com.uravugal.matrimony.utils.RasiLordMapper;

import java.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;


@Service
public class PoruthamService {

    @Autowired
    private KeyValueRepository keyValueRepository;

    @Autowired
    private InterestRequestRepository interestRequestRepository;


    // private final MatchRecordRepository matchRecordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Weights (sum to 100). "vedhai" was missing entirely until 2026-07-17 — the traditional
    // system has 10 poruthams, this only had 9, which is also why this comment claimed "100"
    // while the entries actually summed to 90.
    private static final Map<String,Integer> WEIGHTS = Map.ofEntries(
            Map.entry("nadi", 30),
            Map.entry("rajju", 15),
            Map.entry("gana", 10),
            Map.entry("vedhai", 10),
            Map.entry("yoni", 8),
            Map.entry("mahendra", 6),
            Map.entry("streedhirga", 6),
            Map.entry("rasi", 5),
            Map.entry("rasi_adhipathi", 5),
            Map.entry("vasya", 3),
            Map.entry("dina", 2)
    );

    public int getStarId(String starName) {
        // Find the star configuration from the database
        KeyValue keyValue = keyValueRepository.findByKeyColumn("star")
            .orElseThrow(() -> new RuntimeException("Star configuration not found in the database"));
            
        String json = keyValue.getValueColumn(); // JSON string stored in DB
        System.err.println("json: " + json);
    
        JSONArray array = new JSONArray(json);
    
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            String name = obj.getString("name");
            if (name.equalsIgnoreCase(starName)) {
                return obj.getInt("id");
            }
        }
    
        return 0; // return 0 if not found
    }
    

    // public PoruthamService(MatchRecordRepository repo) {
    //     this.matchRecordRepository = repo;
    // }

    public ResultResponse evaluate(MatchRequestDto req, boolean persist) {
        ResultResponse resultResponse = new ResultResponse();

        // Star Match against another member's real profile (the profile-initiated flow from
        // ProfileDetail.tsx) requires their consent via an accepted interest first — same rule
        // as Voice Call (see ServiceRequestService.createRequest). The standalone horoscope
        // utility (settingsPage.tsx / QuickAccessFAB.tsx) sends neither id, so it's untouched.
        if (req.getRequesterUserId() != null && req.getViewedUserId() != null) {
            try {
                Long requesterUserId = Long.parseLong(new String(Base64.getDecoder().decode(req.getRequesterUserId())));
                Long viewedUserId = Long.parseLong(req.getViewedUserId());
                Optional<InterestRequest> interest = interestRequestRepository.findBetweenUsers(requesterUserId, viewedUserId);
                if (interest.isEmpty() || interest.get().getAcceptStatus() != ApprovalStatus.APPROVED) {
                    resultResponse.setCode(403);
                    resultResponse.setStatus(ResponseStatus.FAILURE);
                    resultResponse.setMessage("INTEREST_NOT_APPROVED");
                    return resultResponse;
                }
            } catch (Exception e) {
                resultResponse.setCode(400);
                resultResponse.setStatus(ResponseStatus.FAILURE);
                resultResponse.setMessage("Invalid requester/viewed user id");
                return resultResponse;
            }
        }

        MatchRequestDto.Profile bride = req.getBride();
        MatchRequestDto.Profile groom = req.getGroom();
        System.out.println("Star: " + groom.getStar());
        System.out.println("bride Star: " + bride.getStar());
        int groomStarId = getStarId(groom.getStar());
        int brideStarId = getStarId(bride.getStar());
        System.out.println("groomStarId: " + groomStarId);
        System.out.println("brideStarId: " + brideStarId);
        
        // int brideNak = safeInt(bride.getStar());
        // int groomNak = safeInt(groom.getStar());
        String brideRasi = bride.getRasi();
        String groomRasi = groom.getRasi();
        System.out.println("brideRasi: " + brideRasi);
        System.out.println("groomRasi: " + groomRasi);

        List<PoruthamResultDto> results = new ArrayList<>();
        int totalScore = 0;
        int totalWeight = WEIGHTS.values().stream().mapToInt(Integer::intValue).sum();

        // Nadi
        PoruthamResultDto nadi = checkNadi(brideStarId,groomStarId);
        results.add(nadi); totalScore += nadi.getScore();

        // Rajju
        PoruthamResultDto rajju = checkRajju(brideStarId,groomStarId);
        results.add(rajju); totalScore += rajju.getScore();

        // Gana
        PoruthamResultDto gana = checkGana(brideStarId,groomStarId);
        results.add(gana); totalScore += gana.getScore();

        // Yoni
        PoruthamResultDto yoni = checkYoni(brideStarId,groomStarId);
        results.add(yoni); totalScore += yoni.getScore();

        // Mahendra
        PoruthamResultDto mahendra = checkMahendra(brideStarId,groomStarId);
        results.add(mahendra); totalScore += mahendra.getScore();

        // Stree Dhirga
        PoruthamResultDto stree = checkStreeDirga(brideStarId,groomStarId);
        results.add(stree); totalScore += stree.getScore();

        // Rasi
        PoruthamResultDto rasi = checkRasi(brideRasi,groomRasi);
        results.add(rasi); totalScore += rasi.getScore();

        // Rasi Adhipathi
        PoruthamResultDto rasiAd = checkRasiAdhipathi(brideRasi,groomRasi);
        results.add(rasiAd); totalScore += rasiAd.getScore();

        // Vasya
        PoruthamResultDto vasya = checkVasya(brideRasi,groomRasi);
        results.add(vasya); totalScore += vasya.getScore();

        // Dina
        PoruthamResultDto dina = checkDina(brideStarId,groomStarId);
        results.add(dina); totalScore += dina.getScore();

        // Vedhai
        PoruthamResultDto vedhai = checkVedhai(brideStarId,groomStarId);
        results.add(vedhai); totalScore += vedhai.getScore();

        int percent = Math.round((totalScore * 100f)/ totalWeight);
        String verdict = percent >= 70 ? "GOOD MATCH" : (percent >= 50 ? "FAIR MATCH" : "NOT RECOMMENDED");

        // Rajju and Vedhai are traditionally mandatory disqualifying filters, not just weighted
        // inputs — a couple can score well on every other porutham and still be considered
        // fundamentally incompatible if either of these fails. The percentage above still
        // reflects the raw weighted score for transparency, but the verdict must say so
        // regardless of how high that percentage is.
        List<String> doshas = new ArrayList<>();
        if ("FAIL".equals(rajju.getResult())) doshas.add("Rajju Dosha");
        if ("FAIL".equals(vedhai.getResult())) doshas.add("Vedhai Dosha");
        if (!doshas.isEmpty()) {
            verdict = "NOT RECOMMENDED (" + String.join(", ", doshas) + ")";
        }

        MatchResponseDto response = new MatchResponseDto(totalScore, percent, verdict, totalWeight, results);

        resultResponse.setCode(200);
        resultResponse.setData(response);
        resultResponse.setMessage("Horoscope Match Results Fetched Successfully.");
        resultResponse.setStatus(ResponseStatus.SUCCESS);

        // if (persist) {
        //     try {
        //         MatchRecord rec = new MatchRecord();
        //         // optionally save profile ids if using user persistence
        //         rec.setScore(totalScore);
        //         rec.setPercentage(percent);
        //         rec.setVerdict(verdict);
        //         rec.setResultJson(objectMapper.writeValueAsString(response));
        //         matchRecordRepository.save(rec);
        //     } catch(Exception ex) {
        //         // log and continue
        //         ex.printStackTrace();
        //     }
        // }

        return resultResponse;
    }

    // private int safeInt(Integer v){ return v == null ? 0 : v; }

    /* --------- Individual Porutham methods --------- */

    private PoruthamResultDto checkNadi(int bNak, int gNak){
        int weight = WEIGHTS.get("nadi");
        String key = "nadi";
        String name = "Nadi Porutham";
        if (bNak <=0 || gNak <=0) return new PoruthamResultDto(key,name,"UNKNOWN","Nakshatra missing",weight,0);

        int bNadi = StarInfo.NADI.getOrDefault(bNak,1);
        int gNadi = StarInfo.NADI.getOrDefault(gNak,1);

        boolean pass = bNadi != gNadi;
        String reason = pass ? "Different Nadi" : "Same Nadi (Nadi Dosha)";
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    private PoruthamResultDto checkRajju(int bNak, int gNak){
        int weight = WEIGHTS.get("rajju");
        String key="rajju"; String name="Rajju Porutham";
        if (bNak<=0||gNak<=0) return new PoruthamResultDto(key,name,"UNKNOWN","Nakshatra missing",weight,0);

        Integer bR = StarInfo.RAJJU.get(bNak);
        Integer gR = StarInfo.RAJJU.get(gNak);
        boolean pass = !Objects.equals(bR, gR);
        String reason = pass ? "Rajju groups not identical (compatible)" : "Same Rajju group (incompatible)";
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    private PoruthamResultDto checkGana(int bNak, int gNak){
        int weight = WEIGHTS.get("gana"); String key="gana"; String name="Gana Porutham";
        if (bNak<=0||gNak<=0) return new PoruthamResultDto(key,name,"UNKNOWN","Nakshatra missing",weight,0);

        String bg = StarInfo.GANA.get(bNak);
        String gg = StarInfo.GANA.get(gNak);
        boolean pass = false;
        if (bg != null && gg != null) {
            if (bg.equals(gg)) pass = true;
            else if ((bg.equals("Deva") && gg.equals("Manushya")) || (bg.equals("Manushya") && gg.equals("Deva"))) pass=true;
        }
        String reason = pass ? "Gana compatible" : String.format("Gana mismatch: %s vs %s",bg,gg);
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    private PoruthamResultDto checkYoni(int bNak, int gNak){
        int weight = WEIGHTS.get("yoni"); String key="yoni"; String name="Yoni Porutham";
        if (bNak<=0||gNak<=0) return new PoruthamResultDto(key,name,"UNKNOWN","Nakshatra missing",weight,0);
        String by = StarInfo.YONI.get(bNak);
        String gy = StarInfo.YONI.get(gNak);
        if (by == null || gy == null) return new PoruthamResultDto(key,name,"UNKNOWN","Yoni unknown",weight,0);
        // The real rule is "avoid enemy animals" (e.g. Snake & Mongoose, Cat & Rat), not "must be
        // the same animal" — same animal is the best case, but any non-enemy pairing is fine too.
        boolean isEnemy = by.equals(StarInfo.YONI_ENEMY.get(gy));
        boolean pass = !isEnemy;
        String reason = pass
                ? String.format("Yoni compatible (%s / %s)", by, gy)
                : String.format("Yoni enmity: %s vs %s", by, gy);
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    private PoruthamResultDto checkMahendra(int bNak, int gNak){
        int weight = WEIGHTS.get("mahendra"); String key="mahendra"; String name="Mahendra Porutham";
        if (bNak<=0||gNak<=0) return new PoruthamResultDto(key,name,"UNKNOWN","Nakshatra missing",weight,0);
        int diff = gNak - bNak; if (diff <= 0) diff += 27;
        boolean pass = Set.of(4,7,10,13,16,19,22,25).contains(diff);
        String reason = pass ? "Mahendra position satisfied" : "Not in Mahendra position";
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    private PoruthamResultDto checkStreeDirga(int bNak, int gNak){
        int weight = WEIGHTS.get("streedhirga"); String key="streedhirga"; String name="Stree Dirga Porutham";
        if (bNak<=0||gNak<=0) return new PoruthamResultDto(key,name,"UNKNOWN","Nakshatra missing",weight,0);
        int diff = gNak - bNak; if (diff < 0) diff += 27;
        boolean pass = diff >= 13; // traditional threshold — was wrongly set to 7
        String reason = pass ? "Sthree Dirgha condition satisfied" : "Not satisfied";
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    // Counting from the bride's rasi (1st), positions 7th/9th/10th/11th/12th are good; 2nd-6th
    // are bad. Position 8 (Ashtama/Randhra — the classic "8th house" affliction almost every
    // Vedic matching tradition flags) is also bad even though the reference this was checked
    // against didn't explicitly list it; same rasi (1st, position with itself) is treated as
    // neutral/compatible, consistent with common practice.
    private static final Set<Integer> RASI_GOOD_POSITIONS = Set.of(1,7,9,10,11,12);

    private PoruthamResultDto checkRasi(String brideRasi, String groomRasi){
        int weight = WEIGHTS.get("rasi"); String key="rasi"; String name="Rasi Porutham";
        if (brideRasi==null || groomRasi==null) return new PoruthamResultDto(key,name,"UNKNOWN","Rasi missing",weight,0);

        int b = mapRasiNameToIndex(brideRasi);
        int g = mapRasiNameToIndex(groomRasi);
        if (b==0 || g==0) return new PoruthamResultDto(key,name,"UNKNOWN","Rasi unknown",weight,0);
        int position = ((g - b + 12) % 12) + 1; // groom's rasi position counted from bride's rasi
        boolean pass = RASI_GOOD_POSITIONS.contains(position);
        String reason = pass ? "Rasi compatible" : "Rasi incompatible (position " + position + " from bride)";
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    private PoruthamResultDto checkRasiAdhipathi(String brideRasi, String groomRasi){
        int weight = WEIGHTS.get("rasi_adhipathi"); String key="rasi_adhipathi"; String name="Rasi Adhipathi Porutham";
        Planet bLord = RasiLordMapper.getLordFromRasi(brideRasi);
        Planet gLord = RasiLordMapper.getLordFromRasi(groomRasi);
        if (bLord == null || gLord == null) return new PoruthamResultDto(key,name,"UNKNOWN","Rasi/lord unknown",weight,0);

        // Standard Vedic Naisargika Maitri (natural friendship) table. The previous version of
        // this table had several inversions (e.g. Mercury/Moon friendship and enmity swapped,
        // Mars/Venus and Jupiter/Saturn enmities wrong) — corrected 2026-07-17.
        Map<Planet, Set<Planet>> friends = new HashMap<>();
        Map<Planet, Set<Planet>> enemies = new HashMap<>();
        friends.put(Planet.SUN, Set.of(Planet.MOON,Planet.MARS,Planet.JUPITER));
        friends.put(Planet.MOON, Set.of(Planet.SUN,Planet.MERCURY));
        friends.put(Planet.MARS, Set.of(Planet.SUN,Planet.MOON,Planet.JUPITER));
        friends.put(Planet.MERCURY, Set.of(Planet.SUN,Planet.VENUS));
        friends.put(Planet.JUPITER, Set.of(Planet.SUN,Planet.MOON,Planet.MARS));
        friends.put(Planet.VENUS, Set.of(Planet.MERCURY,Planet.SATURN));
        friends.put(Planet.SATURN, Set.of(Planet.MERCURY,Planet.VENUS));

        enemies.put(Planet.SUN, Set.of(Planet.VENUS,Planet.SATURN));
        enemies.put(Planet.MOON, Set.of());
        enemies.put(Planet.MARS, Set.of(Planet.MERCURY));
        enemies.put(Planet.MERCURY, Set.of(Planet.MOON));
        enemies.put(Planet.JUPITER, Set.of(Planet.MERCURY,Planet.VENUS));
        enemies.put(Planet.VENUS, Set.of(Planet.SUN,Planet.MOON));
        enemies.put(Planet.SATURN, Set.of(Planet.SUN,Planet.MOON,Planet.MARS));

        String reason;
        String result;
        int score = 0;
        if (bLord == gLord) {
            result = "PASS"; reason = "Same rasi-lord"; score = weight;
        } else if (friends.getOrDefault(bLord, Set.of()).contains(gLord) &&
                   friends.getOrDefault(gLord, Set.of()).contains(bLord)) {
            result = "PASS"; reason = String.format("Lords %s and %s are mutually friendly", bLord, gLord); score = weight;
        } else if (enemies.getOrDefault(bLord, Set.of()).contains(gLord) ||
                   enemies.getOrDefault(gLord, Set.of()).contains(bLord)) {
            result = "FAIL"; reason = String.format("Lords %s and %s are inimical", bLord, gLord); score = 0;
        } else {
            result = "PARTIAL"; reason = String.format("Lords %s and %s neutral", bLord, gLord); score = weight/2;
        }
        return new PoruthamResultDto(key,name,result,reason,weight,score);
    }

    // Vasya groups the 12 rasis into 5 categories. Three rasis (Simha/Dhanusu/Makaram) are
    // traditionally split across two categories by decan in fuller systems; simplified here to
    // one primary category per rasi (matching the common simplification most matching
    // calculators use), since the half-rasi split needs birth-degree data this endpoint doesn't
    // collect. This replaces a stub that only handled 2 hardcoded example pairs.
    private static final Map<Integer,String> VASYA_GROUP = Map.ofEntries(
            Map.entry(1,"Chatushpada"), Map.entry(2,"Chatushpada"), Map.entry(3,"Manava"),
            Map.entry(4,"Jalachara"), Map.entry(5,"Vanachara"), Map.entry(6,"Manava"),
            Map.entry(7,"Manava"), Map.entry(8,"Keeta"), Map.entry(9,"Manava"),
            Map.entry(10,"Chatushpada"), Map.entry(11,"Manava"), Map.entry(12,"Jalachara")
    );

    private PoruthamResultDto checkVasya(String brideRasi, String groomRasi){
        int weight = WEIGHTS.get("vasya"); String key="vasya"; String name="Vasya Porutham";
        if (brideRasi==null || groomRasi==null) return new PoruthamResultDto(key,name,"UNKNOWN","Rasi missing",weight,0);
        int b = mapRasiNameToIndex(brideRasi);
        int g = mapRasiNameToIndex(groomRasi);
        if (b==0 || g==0) return new PoruthamResultDto(key,name,"UNKNOWN","Rasi unknown",weight,0);

        String bGroup = VASYA_GROUP.get(b);
        String gGroup = VASYA_GROUP.get(g);
        boolean pass = (b == g) // same rasi
                || (bGroup.equals(gGroup) && !bGroup.equals("Keeta")) // same group (Keeta only pairs with itself)
                || (Set.of(bGroup, gGroup).equals(Set.of("Chatushpada", "Vanachara")))
                || (Set.of(bGroup, gGroup).equals(Set.of("Manava", "Jalachara")));
        String reason = pass ? String.format("Vasya compatible (%s / %s)", bGroup, gGroup) : String.format("Vasya not compatible (%s / %s)", bGroup, gGroup);
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    private PoruthamResultDto checkDina(int bNak, int gNak){
        int weight = WEIGHTS.get("dina"); String key="dina"; String name="Dina Porutham";
        if (bNak<=0||gNak<=0) return new PoruthamResultDto(key,name,"UNKNOWN","Nakshatra missing",weight,0);
        int diff = Math.abs(bNak - gNak);
        int res = diff % 9;
        // Was inverted: remainder 0/2/4/6/8 is the GOOD case (per standard Dina/Tara Porutham
        // rule), 1/3/5/7 is bad — this previously passed on 1/3/5/7 instead.
        boolean pass = (res==0 || res==2 || res==4 || res==6 || res==8);
        String reason = pass ? "Dina compatible" : "Dina not compatible";
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    private PoruthamResultDto checkVedhai(int bNak, int gNak){
        int weight = WEIGHTS.get("vedhai"); String key="vedhai"; String name="Vedhai Porutham";
        if (bNak<=0||gNak<=0) return new PoruthamResultDto(key,name,"UNKNOWN","Nakshatra missing",weight,0);
        Integer vedhaPartner = StarInfo.VEDHA.get(bNak);
        boolean pass = vedhaPartner == null || !vedhaPartner.equals(gNak);
        String reason = pass ? "No Vedhai affliction" : "Vedhai (mutual star enmity) present";
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    /* Utility mapping for rasi name -> index (1..12). Expand names if you use Tamil labels */
    private int mapRasiNameToIndex(String rasi) {
        if (rasi == null) return 0;
        String r = rasi.trim().toLowerCase();
        return switch (r) {
            case "mesham","aries" -> 1;
            case "rishabam","taurus" -> 2;
            case "mithunam","gemini" -> 3;
            // "kadagam" is the spelling StarMatch.tsx's rasiData list actually sends for Cancer —
            // without this alias every Cancer submission fell through to the default/unknown case.
            case "katagam","kadagam","cancer" -> 4;
            case "simham","simmam","leo" -> 5;
            case "kanni","virgo" -> 6;
            case "thulam","libra" -> 7;
            case "vrischikam","viruchagam","scorpio" -> 8;
            case "dhanusu","sagittarius" -> 9;
            case "magaram","makaram","capricorn" -> 10;
            case "kumbam","aquarius" -> 11;
            case "meenam","pisces" -> 12;
            default -> 0;
        };
    }
}
