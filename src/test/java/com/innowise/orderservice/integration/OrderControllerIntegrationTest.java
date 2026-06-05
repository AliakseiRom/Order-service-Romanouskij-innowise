package com.innowise.orderservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.innowise.orderservice.OrderserviceApplication;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.utils.Status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(classes = OrderserviceApplication.class)
class OrderControllerIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_service_test")
            .withUsername("test")
            .withPassword("test");

    private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(options().dynamicPort());

    static {
        POSTGRES.start();
        WIRE_MOCK_SERVER.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("user-service.url", WIRE_MOCK_SERVER::baseUrl);
    }

    @AfterAll
    static void stopContainers() {
        WIRE_MOCK_SERVER.stop();
        POSTGRES.stop();
    }

    @BeforeEach
    void setUp() {
        WIRE_MOCK_SERVER.resetAll();

        jdbcTemplate.execute("TRUNCATE TABLE order_items, orders, items RESTART IDENTITY CASCADE");

        jdbcTemplate.update("""
                INSERT INTO items (name, price, created_at, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Phone", 100L);

        jdbcTemplate.update("""
                INSERT INTO items (name, price, created_at, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Mouse", 50L);

        stubUserByEmail("test@gmail.com");
    }

    @Test
    void createOrder_shouldReturnCreatedOrderWithUserInfo() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserEmail("test@gmail.com");
        request.setItems(List.of(
                orderItemRequest(1L, 2),
                orderItemRequest(2L, 1)
        ));

        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.userEmail").value("test@gmail.com"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalPrice").value(250))
                .andExpect(jsonPath("$.deleted").value(false))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.user.email").value("test@gmail.com"))
                .andExpect(jsonPath("$.user.name").value("Ivan"));
    }

    @Test
    void getOrderById_shouldReturnOrderWithUserInfo() throws Exception {
        insertOrder();

        mockMvc.perform(get("/orders/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.userEmail").value("test@gmail.com"))
                .andExpect(jsonPath("$.user.email").value("test@gmail.com"))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void getOrders_shouldReturnPageWithUserInfo() throws Exception {
        insertOrder();

        mockMvc.perform(get("/orders")
                        .param("page", "0")
                        .param("size", "10")
                        .param("statuses", "CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(10))
                .andExpect(jsonPath("$.content[0].user.email").value("test@gmail.com"));
    }

    @Test
    void getOrdersByUserId_shouldReturnOrdersWithUserInfo() throws Exception {
        insertOrder();

        mockMvc.perform(get("/orders/user/{userId}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(10))
                .andExpect(jsonPath("$[0].user.email").value("test@gmail.com"));
    }

    @Test
    void updateOrder_shouldReturnUpdatedOrderWithUserInfo() throws Exception {
        insertOrder();

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus(Status.PAID);
        request.setItems(List.of(orderItemRequest(2L, 3)));

        mockMvc.perform(put("/orders/{id}", 1L)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalPrice").value(150))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.user.email").value("test@gmail.com"));
    }

    @Test
    void deleteOrder_shouldSoftDeleteOrder() throws Exception {
        insertOrder();

        mockMvc.perform(delete("/orders/{id}", 1L))
                .andExpect(status().isNoContent());

        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM orders WHERE id = ?",
                Boolean.class,
                1L
        );

        assertThat(deleted).isTrue();
    }

    @Test
    void createOrder_shouldReturnBadRequest_whenDuplicateItemId() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserEmail("test@gmail.com");
        request.setItems(List.of(
                orderItemRequest(1L, 1),
                orderItemRequest(1L, 2)
        ));

        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate item id in order: 1"));
    }

    @Test
    void createOrder_shouldReturnNotFound_whenItemsNotFound() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserEmail("test@gmail.com");
        request.setItems(List.of(orderItemRequest(999L, 1)));

        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Some items were not found"));
    }

    @Test
    void createOrder_shouldReturnServiceUnavailable_whenUserServiceIsUnavailable() throws Exception {
        WIRE_MOCK_SERVER.resetAll();

        WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/user/email"))
                .withQueryParam("email", equalTo("test@gmail.com"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "message": "Internal user service error"
                            }
                            """)));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserEmail("test@gmail.com");
        request.setItems(List.of(orderItemRequest(1L, 1)));

        mockMvc.perform(post("/orders")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("User service unavailable"));
    }

    private OrderItemRequest orderItemRequest(Long itemId, Integer quantity) {
        OrderItemRequest request = new OrderItemRequest();
        request.setItemId(itemId);
        request.setQuantity(quantity);
        return request;
    }

    private void insertOrder() {
        jdbcTemplate.update("""
                INSERT INTO orders (user_id, user_email, status, total_price, deleted, created_at, updated_at)
                VALUES (?, ?, ?, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, 10L, "test@gmail.com", "CREATED", 100L);

        jdbcTemplate.update("""
                INSERT INTO order_items (order_id, item_id, quantity, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, 1L, 1L, 1);
    }

    private void stubUserByEmail(String email) {
        WIRE_MOCK_SERVER.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/user/email"))
                .withQueryParam("email", equalTo(email))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": 10,
                                  "name": "Ivan",
                                  "surname": "Ivanov",
                                  "birthDate": "2000-01-01",
                                  "email": "test@gmail.com",
                                  "active": true,
                                  "createdAt": "2026-06-01T10:00:00",
                                  "updatedAt": "2026-06-01T10:00:00"
                                }
                                """)));
    }
}