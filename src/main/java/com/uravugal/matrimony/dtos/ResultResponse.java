package com.uravugal.matrimony.dtos;

import com.uravugal.matrimony.enums.ResponseStatus;

import lombok.Data;

@Data
public class ResultResponse {
	private int code;
	private ResponseStatus status;
	private String message;
	private Object data;
}
