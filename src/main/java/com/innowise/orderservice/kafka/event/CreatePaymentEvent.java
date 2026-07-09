package com.innowise.orderservice.kafka.event;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreatePaymentEvent {

    private String eventType;

    private Long paymentId;

    private Long orderId;

    private Long userId;

    private String paymentStatus;

    private BigDecimal paymentAmount;

    private LocalDateTime timestamp;
}
