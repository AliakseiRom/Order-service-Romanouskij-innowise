package com.innowise.orderservice.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.orderservice.kafka.event.CreatePaymentEvent;
import com.innowise.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;

    private final OrderService orderService;

    @KafkaListener(
            topics = "${kafka.topics.create-payment}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleCreatePaymentEvent(String message) {
        try {
            CreatePaymentEvent event = objectMapper.readValue(
                    message,
                    CreatePaymentEvent.class
            );

            if (!"CREATE_PAYMENT".equals(event.getEventType())) {
                return;
            }

            orderService.handleCreatePaymentEvent(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize CREATE_PAYMENT event", e);
        }
    }
}
