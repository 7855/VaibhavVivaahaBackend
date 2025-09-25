package com.uravugal.matrimony.models;

import java.util.Date;



import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.uravugal.matrimony.enums.ActiveStatus;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Data;

@Data
@MappedSuperclass
public class GenericEntity {

	@Temporal(TemporalType.TIMESTAMP)
	@CreatedDate
	@Column(name = "createdAt", updatable = false)
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private Date createdAt;

	@Temporal(TemporalType.TIMESTAMP)
	@LastModifiedDate
	@Column(name = "updatedAt")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private Date updatedAt;

	@Column(length = 32, columnDefinition = "enum('Y','N') default 'Y'")
	@Enumerated(value = EnumType.STRING)
	private ActiveStatus isActive = ActiveStatus.Y;

	@Column(name = "updatedBy")
	@JsonFormat
	private String updatedBy;

	@Column(name = "createdBy")
	@JsonFormat
	private String createdBy;
	
	
	@PrePersist protected void prePersist() {
		createdAt = new Date();
		updatedAt = new Date(); 
	}

	@PreUpdate protected void preUpdate()  {
		updatedAt = new Date(); 
	}
}

