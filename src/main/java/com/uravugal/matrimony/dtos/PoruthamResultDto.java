package com.uravugal.matrimony.dtos;

public class PoruthamResultDto {
    private String key;
    private String name;
    private String result;   // PASS / FAIL / PARTIAL / UNKNOWN
    private String reason;
    private int weight;
    private int score;

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
}
