package com.innowise.orderservice.dto.request;

import lombok.Data;

@Data
public class OrderItemRequest {

    private Long itemId;

    private Integer quantity;
}
