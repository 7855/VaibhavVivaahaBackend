package com.uravugal.matrimony.dtos;

public class PoruthamResultDto {
    private String key;
    private String name;
    private String result;   // PASS / PARTIAL / FAIL / UNKNOWN (kept for older app builds)
    private String reason;
    private int weight;      // always 1 — one porutham (kept for older app builds)
    private int score;       // 1 only for Uthamam (kept for older app builds)

    // Tamil 10-porutham fields (2026-09-27)
    private String grade;    // UTHAMAM / MADHIMAM / ADHAMAM / UNKNOWN
    private double points;   // 1 / 0.5 / 0
    private boolean essential; // one of the five mukkiya poruthams (Dina, Gana, Yoni, Rasi, Rajju)
    private boolean counted;   // false for informational extras such as Nadi

    public PoruthamResultDto() {}

    public PoruthamResultDto(String key, String name, String result, String reason, int weight, int score) {
        this.key = key; this.name = name; this.result = result; this.reason = reason;
        this.weight = weight; this.score = score;
    }
    // getters & setters
    public String getKey(){return key;} public void setKey(String k){this.key=k;}
    public String getName(){return name;} public void setName(String n){this.name=n;}
    public String getResult(){return result;} public void setResult(String r){this.result=r;}
    public String getReason(){return reason;} public void setReason(String s){this.reason=s;}
    public int getWeight(){return weight;} public void setWeight(int w){this.weight=w;}
    public int getScore(){return score;} public void setScore(int s){this.score=s;}
    public String getGrade(){return grade;} public void setGrade(String g){this.grade=g;}
    public double getPoints(){return points;} public void setPoints(double p){this.points=p;}
    public boolean isEssential(){return essential;} public void setEssential(boolean e){this.essential=e;}
    public boolean isCounted(){return counted;} public void setCounted(boolean c){this.counted=c;}
}
