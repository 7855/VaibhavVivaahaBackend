package com.uravugal.matrimony.config;

import com.uravugal.matrimony.dtos.ResultResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultResponse handleValidationErrors(MethodArgumentNotValidException ex) {
        ResultResponse response = new ResultResponse();
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        response.setCode(400);
        response.setStatus(com.uravugal.matrimony.enums.ResponseStatus.FAILURE);
        response.setMessage("Validation failed: " + errors);
        return response;
    }
}