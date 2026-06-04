package com.innowise.orderservice.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorDetails {

    private Integer statusCode;
    private String message;
    private String details;

    public ErrorDetails(Integer statusCode, String message, String details) {
        this.statusCode = statusCode;
        this.message = message;
        this.details = details;
    }
}