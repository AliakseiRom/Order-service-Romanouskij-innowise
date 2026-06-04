package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.response.UserResponse;
import com.innowise.orderservice.exception.UserNotFoundException;
import com.innowise.orderservice.exception.UserServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class UserClient {

    private final RestClient userServiceRestClient;

    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserByEmailFallback")
    public UserResponse getUserByEmail(String email) {
        try {
            return userServiceRestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/email")
                            .queryParam("email", email)
                            .build())
                    .retrieve()
                    .body(UserResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new UserNotFoundException("User not found with email: " + email);
        }
    }

    private UserResponse getUserByEmailFallback(String email, Throwable throwable) {
        if (throwable instanceof UserNotFoundException) {
            throw (UserNotFoundException) throwable;
        }

        throw new UserServiceUnavailableException("User service unavailable");
    }
}