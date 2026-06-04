package com.innowise.orderservice.exception;

public class OrderNotFoundException extends CommonException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}
