package com.uravugal.matrimony.models;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "happyStories")
public class HappyStoryEntity extends GenericEntity{
    @Id
    @Column(name = "happystoryId", length = 10)
    private String happystoryId;

    @Column(name = "coupleNames")
    private String coupleNames;

    @Column(name = "story", length = 500)
    private String story;

    @Column(name = "partner1Image")
    private String partner1Image;

    @Column(name = "partner2Image")
    private String partner2Image;

    @Column(name = "marriageDate")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date marriageDate;

}
