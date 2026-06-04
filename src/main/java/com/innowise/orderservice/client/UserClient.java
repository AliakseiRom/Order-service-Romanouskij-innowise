package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.response.UserResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class UserClient {

    private final RestClient userServiceRestClient;

    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserByEmailFallback")
    public UserResponse getUserByEmail(String email) {
        return userServiceRestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/user/email")
                        .queryParam("email", email)
                        .build())
                .retrieve()
                .body(UserResponse.class);
    }

    private UserResponse getUserByEmailFallback(String email, Throwable throwable) {
        throw new RuntimeException("User Service is unavailable");
    }
}
