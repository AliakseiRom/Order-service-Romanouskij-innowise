package com.innowise.orderservice.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserResponse {

    private Long id;

    private String name;

    private String surname;

    private LocalDate birthDate;

    private String email;

    private boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
