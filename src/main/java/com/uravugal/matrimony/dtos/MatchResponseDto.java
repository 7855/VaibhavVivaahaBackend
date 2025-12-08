package com.uravugal.matrimony.dtos;

import java.util.List;

public class MatchResponseDto {
    private int score;
    private int percentage;
    private String verdict;
    private int totalWeight;
    private List<PoruthamResultDto> results;

    public MatchResponseDto() {}

    public MatchResponseDto(int score, int percentage, String verdict, int totalWeight, List<PoruthamResultDto> results){
        this.score = score; this.percentage = percentage; this.verdict = verdict; this.totalWeight = totalWeight; this.results = results;
    }

    // getters & setters
    public int getScore(){return score;} public void setScore(int s){this.score=s;}
    public int getPercentage(){return percentage;} public void setPercentage(int p){this.percentage=p;}
    public String getVerdict(){return verdict;} public void setVerdict(String v){this.verdict=v;}
    public int getTotalWeight(){return totalWeight;} public void setTotalWeight(int t){this.totalWeight=t;}
    public List<PoruthamResultDto> getResults(){return results;} public void setResults(List<PoruthamResultDto> r){this.results=r;}
}
