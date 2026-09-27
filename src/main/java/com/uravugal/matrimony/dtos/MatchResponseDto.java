package com.uravugal.matrimony.dtos;

import java.util.List;

public class MatchResponseDto {
    // Kept for older app builds: score/percentage are the porutham points × 10 (out of 100).
    private int score;
    private int percentage;
    private String verdict;
    private int totalWeight;
    private List<PoruthamResultDto> results;

    // Tamil 10-porutham headline (2026-09-27)
    private double poruthamPoints;   // e.g. 7.5
    private int totalPoruthams;      // 10
    private int uthamamCount;
    private int madhimamCount;
    private int adhamamCount;
    private int essentialsPassed;    // of the five mukkiya poruthams
    private int essentialsTotal;
    private List<String> doshams;    // "Rajju Dosham", "Vedhai Dosham"
    private String verdictNote;
    private List<PoruthamResultDto> extras; // informational, not counted (Nadi)
    private String brideStar, groomStar, brideRasi, groomRasi; // canonical names actually used

    public MatchResponseDto() {}

    public MatchResponseDto(int score, int percentage, String verdict, int totalWeight, List<PoruthamResultDto> results){
        this.score = score; this.percentage = percentage; this.verdict = verdict; this.totalWeight = totalWeight; this.results = results;
    }

    public int getScore(){return score;} public void setScore(int s){this.score=s;}
    public int getPercentage(){return percentage;} public void setPercentage(int p){this.percentage=p;}
    public String getVerdict(){return verdict;} public void setVerdict(String v){this.verdict=v;}
    public int getTotalWeight(){return totalWeight;} public void setTotalWeight(int t){this.totalWeight=t;}
    public List<PoruthamResultDto> getResults(){return results;} public void setResults(List<PoruthamResultDto> r){this.results=r;}
    public double getPoruthamPoints(){return poruthamPoints;} public void setPoruthamPoints(double p){this.poruthamPoints=p;}
    public int getTotalPoruthams(){return totalPoruthams;} public void setTotalPoruthams(int t){this.totalPoruthams=t;}
    public int getUthamamCount(){return uthamamCount;} public void setUthamamCount(int c){this.uthamamCount=c;}
    public int getMadhimamCount(){return madhimamCount;} public void setMadhimamCount(int c){this.madhimamCount=c;}
    public int getAdhamamCount(){return adhamamCount;} public void setAdhamamCount(int c){this.adhamamCount=c;}
    public int getEssentialsPassed(){return essentialsPassed;} public void setEssentialsPassed(int c){this.essentialsPassed=c;}
    public int getEssentialsTotal(){return essentialsTotal;} public void setEssentialsTotal(int c){this.essentialsTotal=c;}
    public List<String> getDoshams(){return doshams;} public void setDoshams(List<String> d){this.doshams=d;}
    public String getVerdictNote(){return verdictNote;} public void setVerdictNote(String n){this.verdictNote=n;}
    public List<PoruthamResultDto> getExtras(){return extras;} public void setExtras(List<PoruthamResultDto> e){this.extras=e;}
    public String getBrideStar(){return brideStar;} public void setBrideStar(String s){this.brideStar=s;}
    public String getGroomStar(){return groomStar;} public void setGroomStar(String s){this.groomStar=s;}
    public String getBrideRasi(){return brideRasi;} public void setBrideRasi(String s){this.brideRasi=s;}
    public String getGroomRasi(){return groomRasi;} public void setGroomRasi(String s){this.groomRasi=s;}
}
