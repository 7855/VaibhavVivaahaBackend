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

    // Present only for the profile-initiated Star Match flow (ProfileDetail.tsx -> StarMatch.tsx).
    // The standalone horoscope-utility flow (settingsPage.tsx / QuickAccessFAB.tsx) omits both, and
    // PoruthamService only enforces the interest-approval gate when both are non-null.
    private String requesterUserId; // base64-encoded, same encoding used across the app's other endpoints
    private String viewedUserId; // plain numeric id (as a string), matches ServiceRequestService's targetUserId convention

    public MatchRequestDto() {}
    public Profile getBride(){return bride;} public void setBride(Profile b){this.bride=b;}
    public Profile getGroom(){return groom;} public void setGroom(Profile g){this.groom=g;}
    public String getRequesterUserId(){return requesterUserId;} public void setRequesterUserId(String r){this.requesterUserId=r;}
    public String getViewedUserId(){return viewedUserId;} public void setViewedUserId(String v){this.viewedUserId=v;}
}

