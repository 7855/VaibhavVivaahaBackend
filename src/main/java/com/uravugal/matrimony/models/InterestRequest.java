package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;

import com.uravugal.matrimony.enums.ApprovalStatus;

@Data
@Entity
@Table(name = "interestRequest")
public class InterestRequest extends GenericEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interestSend")
    private Long interestSend;

    @Column(name = "interestReceived")
    private Long interestReceived;

    @Enumerated(EnumType.STRING)
    @Column(name = "acceptStatus")
    private ApprovalStatus acceptStatus = ApprovalStatus.PENDING;

}
