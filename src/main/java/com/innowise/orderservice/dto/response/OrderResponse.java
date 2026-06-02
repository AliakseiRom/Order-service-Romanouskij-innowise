package com.innowise.orderservice.dto.response;

import com.innowise.orderservice.utils.Status;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponse {

    private Long id;

    private Long userId;

    private Status status;

    private Long totalPrice;

    private Boolean deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<OrderItemResponse> items;
}
