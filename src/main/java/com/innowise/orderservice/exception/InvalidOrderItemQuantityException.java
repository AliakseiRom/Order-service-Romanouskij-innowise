package com.innowise.orderservice.exception;

public class InvalidOrderItemQuantityException extends CommonException {
    public InvalidOrderItemQuantityException(String message) {
        super(message);
    }
}
