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
import com.uravugal.matrimony.enums.Planet;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.KeyValue;
import com.uravugal.matrimony.repositories.KeyValueRepository;
import com.uravugal.matrimony.utils.StarInfo;
import com.uravugal.matrimony.utils.RasiLordMapper;


import org.json.JSONArray;
import org.json.JSONObject;


@Service
public class PoruthamService {

    @Autowired
    private KeyValueRepository keyValueRepository;
    
    
    // private final MatchRecordRepository matchRecordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Weights (sum to 100)
    private static final Map<String,Integer> WEIGHTS = Map.ofEntries(
            Map.entry("nadi", 30),
            Map.entry("rajju", 15),
            Map.entry("gana", 10),
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

        int percent = Math.round((totalScore * 100f)/ totalWeight);
        String verdict = percent >= 70 ? "GOOD MATCH" : (percent >= 50 ? "FAIR MATCH" : "NOT RECOMMENDED");

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
        boolean pass = by != null && by.equals(gy);
        String reason = pass ? "Yoni same" : String.format("Yoni mismatch: %s vs %s", by, gy);
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
        boolean pass = diff >= 7; // simplified rule used earlier
        String reason = pass ? "Sthree Dirgha condition satisfied" : "Not satisfied";
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    private PoruthamResultDto checkRasi(String brideRasi, String groomRasi){
        int weight = WEIGHTS.get("rasi"); String key="rasi"; String name="Rasi Porutham";
        if (brideRasi==null || groomRasi==null) return new PoruthamResultDto(key,name,"UNKNOWN","Rasi missing",weight,0);

        // Convert to rasi id if available via simple mapping; here we estimate by string difference not implemented.
        // Use simplified rule: if opposite (6 signs apart) or 8 apart -> fail
        int b = mapRasiNameToIndex(brideRasi);
        int g = mapRasiNameToIndex(groomRasi);
        if (b==0 || g==0) return new PoruthamResultDto(key,name,"UNKNOWN","Rasi unknown",weight,0);
        int diff = Math.abs(b - g);
        boolean pass = !(diff == 6 || diff == 8);
        String reason = pass ? "Rasi compatible" : "Rasi incompatible (bad distance)";
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    private PoruthamResultDto checkRasiAdhipathi(String brideRasi, String groomRasi){
        int weight = WEIGHTS.get("rasi_adhipathi"); String key="rasi_adhipathi"; String name="Rasi Adhipathi Porutham";
        Planet bLord = RasiLordMapper.getLordFromRasi(brideRasi);
        Planet gLord = RasiLordMapper.getLordFromRasi(groomRasi);
        if (bLord == null || gLord == null) return new PoruthamResultDto(key,name,"UNKNOWN","Rasi/lord unknown",weight,0);

        // planet friendship/enemy maps (simplified)
        Map<Planet, Set<Planet>> friends = new HashMap<>();
        Map<Planet, Set<Planet>> enemies = new HashMap<>();
        friends.put(Planet.SUN, Set.of(Planet.MOON,Planet.MARS,Planet.JUPITER));
        friends.put(Planet.MOON, Set.of(Planet.SUN,Planet.MERCURY));
        friends.put(Planet.MARS, Set.of(Planet.SUN,Planet.MOON,Planet.JUPITER));
        friends.put(Planet.MERCURY, Set.of(Planet.MOON,Planet.VENUS));
        friends.put(Planet.JUPITER, Set.of(Planet.SUN,Planet.MOON,Planet.MARS));
        friends.put(Planet.VENUS, Set.of(Planet.MERCURY,Planet.SATURN));
        friends.put(Planet.SATURN, Set.of(Planet.MERCURY,Planet.VENUS));

        enemies.put(Planet.SUN, Set.of(Planet.SATURN));
        enemies.put(Planet.MOON, Set.of(Planet.MARS));
        enemies.put(Planet.MARS, Set.of(Planet.VENUS));
        enemies.put(Planet.MERCURY, Set.of());
        enemies.put(Planet.JUPITER, Set.of(Planet.SATURN));
        enemies.put(Planet.VENUS, Set.of(Planet.MARS));
        enemies.put(Planet.SATURN, Set.of(Planet.SUN,Planet.JUPITER));

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

    private PoruthamResultDto checkVasya(String brideRasi, String groomRasi){
        int weight = WEIGHTS.get("vasya"); String key="vasya"; String name="Vasya Porutham";
        if (brideRasi==null || groomRasi==null) return new PoruthamResultDto(key,name,"UNKNOWN","Rasi missing",weight,0);
        int b = mapRasiNameToIndex(brideRasi);
        int g = mapRasiNameToIndex(groomRasi);
        if (b==0 || g==0) return new PoruthamResultDto(key,name,"UNKNOWN","Rasi unknown",weight,0);

        // simplified subset: identical or special compatible pairs
        boolean pass = (b == g) ||
                (b==1 && (g==5 || g==9)) || // Aries compatible example
                (b==2 && (g==4|| g==11)); // sample pairs; expand as needed
        String reason = pass ? "Vasya compatible" : "Vasya not compatible";
        int score = pass ? weight : 0;
        return new PoruthamResultDto(key,name, pass? "PASS":"FAIL", reason, weight, score);
    }

    private PoruthamResultDto checkDina(int bNak, int gNak){
        int weight = WEIGHTS.get("dina"); String key="dina"; String name="Dina Porutham";
        if (bNak<=0||gNak<=0) return new PoruthamResultDto(key,name,"UNKNOWN","Nakshatra missing",weight,0);
        int diff = Math.abs(bNak - gNak);
        int res = diff % 9;
        boolean pass = !(res==0 || res==2 || res==4 || res==6 || res==8);
        String reason = pass ? "Dina compatible" : "Dina not compatible";
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
            case "katagam","cancer" -> 4;
            case "simham","leo" -> 5;
            case "kanni","virgo" -> 6;
            case "thulam","libra" -> 7;
            case "vrischikam","scorpio" -> 8;
            case "dhanusu","sagittarius" -> 9;
            case "magaram","capricorn" -> 10;
            case "kumbam","aquarius" -> 11;
            case "meenam","pisces" -> 12;
            default -> 0;
        };
    }
}
