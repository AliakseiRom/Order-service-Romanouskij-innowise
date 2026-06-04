package com.innowise.orderservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderItemRequest {

    @NotNull
    private Long itemId;

    @NotNull
    private Integer quantity;
}
