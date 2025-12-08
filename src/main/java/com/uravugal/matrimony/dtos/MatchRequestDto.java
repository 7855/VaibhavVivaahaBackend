package com.uravugal.matrimony.dtos;

public class MatchRequestDto {
    public static class Profile {
        private String name;
        private String dob; // yyyy-MM-dd (not used for porutham checks here)
        private String timeOfBirth; // HH:mm
        private String place;
        private String star; // 1..27
        private String rasi; // textual rasi name (preferred)
        // private Integer pada; // 1..4 optional

        // getters & setters omitted for brevity (add them)
        public String getName(){return name;} public void setName(String n){this.name=n;}
        public String getDob(){return dob;} public void setDob(String d){this.dob=d;}
        public String getTimeOfBirth(){return timeOfBirth;} public void setTimeOfBirth(String t){this.timeOfBirth=t;}
        public String getPlace(){return place;} public void setPlace(String p){this.place=p;}
        public String getStar(){return star;} public void setStar(String i){this.star=i;}
        // public Integer getRasiId(){return rasiId;} public void setRasiId(Integer i){this.rasiId=i;}
        public String getRasi(){return rasi;} public void setRasi(String r){this.rasi=r;}
        // public Integer getPada(){return pada;} public void setPada(Integer p){this.pada=p;}
    }

    private Profile bride;
    private Profile groom;

    public MatchRequestDto() {}
    public Profile getBride(){return bride;} public void setBride(Profile b){this.bride=b;}
    public Profile getGroom(){return groom;} public void setGroom(Profile g){this.groom=g;}
}

