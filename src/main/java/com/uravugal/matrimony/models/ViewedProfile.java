package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;


@Data
@Entity
@Table(name = "viewedProfile")
public class ViewedProfile extends GenericEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userIdValue")
    private Long userIdValue;

    @Column(name = "viewedBy")
    private Long viewedBy;

    // @Enumerated(EnumType.STRING)
    // @Column(name = "userRead")
    // private UserKnowStatus userRead = UserKnowStatus.NO;

}
