package com.innowise.orderservice.dto.request;

import com.innowise.orderservice.utils.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateOrderRequest {

    @NotBlank
    private Status status;

    @NotNull
    private List<OrderItemRequest> items;
}
