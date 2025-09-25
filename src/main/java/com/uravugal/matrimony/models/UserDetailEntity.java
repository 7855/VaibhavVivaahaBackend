package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;

import com.uravugal.matrimony.enums.EmploymentType;

@Data
@Entity
@Table(name = "user_details")
public class UserDetailEntity extends GenericEntity{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId")
    private Long userId;

    @Column(name = "permanentAddress")
    private String permanentAddress;

    @Column(name = "presentAddress")
    private String presentAddress;

    @Column(name = "familyInfo", columnDefinition = "json")
    private String familyInfo; // Store JSON string like: {"fatherName":"",...}

    @Column(name = "astroInfo", columnDefinition = "json")
    private String astronomicInfo; // JSON string: {"sun_sign":"",...}

    @Column(name = "aboutFamily")
    private String aboutFamily;

    @Column(name = "about")
    private String about;

    @Column(name = "basicInfo", columnDefinition = "json")
    private String basicInfo; 
    
    @Column(name = "hobbies", columnDefinition = "json")
    private String hobbies;

    @Column(name = "languages", columnDefinition = "json")
    private String languages;

    @Column(name = "height")
    private String height;

    @Column(name = "weight")
    private String weight;

    @Column(name = "occupation")
    private String occupation;

    @Column(name = "annualIncome")
    private String annualIncome;

    @Column(name = "degree")
    private String degree;

    @Column(name = "horoscope")
    private String horoscope;

    @Enumerated(EnumType.STRING)
    @Column(name = "employedAt")
    private EmploymentType employedAt;

    @Column(name = "jobPlace")
    private String jobPlace;

    @Column(name = "connectionCount")
    private Integer connectionCount;

    @Column(name = "visitCount")
    private Integer visitCount;

    @Column(name = "interestCount")
    private Integer interestCount;
}
    