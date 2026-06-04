package com.innowise.orderservice.dto.response;

import lombok.Data;

@Data
public class ItemResponse {

    private Long id;

    private String name;

    private Long price;
}
