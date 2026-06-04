package com.innowise.orderservice.exception;

public class DuplicateOrderItemException extends CommonException {
    public DuplicateOrderItemException(String message) {
        super(message);
    }
}
