package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "gallery")
public class GalleryEntity extends GenericEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "galleryId")
    private Long galleryId;

    @Column(name = "userImage")
    private String userImage;

    @Column(name = "userId")
    private Long userId;
}
