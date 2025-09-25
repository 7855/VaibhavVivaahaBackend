package com.uravugal.matrimony.dtos;

import lombok.Data;
import java.util.Date;

@Data
public class ViewedProfileDetailDTO {
    private Long userId;
    private String memberId;
    private String firstName;
    private String lastName;
    private Integer age;
    private String gender;
    private String email;
    private String mobile;
    private String profileImage;
    private String occupation;
    private String location;
    private String degree;
    private String annualIncome;
    private Date viewedAt;
}
