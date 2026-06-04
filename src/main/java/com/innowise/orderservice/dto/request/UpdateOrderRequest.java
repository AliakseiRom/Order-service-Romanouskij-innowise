package com.innowise.orderservice.dto.request;

import com.innowise.orderservice.utils.Status;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class UpdateOrderRequest {

    private Status status;

    private List<@Valid OrderItemRequest> items;
}
