package com.innowise.orderservice.unit;

import com.innowise.orderservice.client.UserClient;
import com.innowise.orderservice.dto.request.CreateOrderRequest;
import com.innowise.orderservice.dto.request.OrderFilterRequest;
import com.innowise.orderservice.dto.request.OrderItemRequest;
import com.innowise.orderservice.dto.request.UpdateOrderRequest;
import com.innowise.orderservice.dto.response.OrderResponse;
import com.innowise.orderservice.dto.response.UserResponse;
import com.innowise.orderservice.exception.DuplicateOrderItemException;
import com.innowise.orderservice.exception.InvalidOrderItemQuantityException;
import com.innowise.orderservice.exception.InvalidOrderItemsException;
import com.innowise.orderservice.exception.ItemIdRequiredException;
import com.innowise.orderservice.exception.ItemsNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderItemMapper;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.service.OrderService;
import com.innowise.orderservice.utils.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_shouldCreateOrderAndReturnResponseWithUserInfo() {
        CreateOrderRequest request = createOrderRequest("test@gmail.com", 1L, 2);
        UserResponse user = userResponse(10L, "test@gmail.com");
        Order order = new Order();
        Item item = item(1L, "Phone", 100L);
        OrderItem orderItem = orderItem(2);
        OrderResponse mappedResponse = new OrderResponse();

        when(userClient.getUserByEmail("test@gmail.com")).thenReturn(user);
        when(orderMapper.toOrder(request)).thenReturn(order);
        when(itemRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(item));
        when(orderItemMapper.toOrderItem(request.getItems().get(0))).thenReturn(orderItem);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            savedOrder.setId(1L);
            return savedOrder;
        });
        when(orderMapper.toOrderResponse(any(Order.class))).thenReturn(mappedResponse);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result).isSameAs(mappedResponse);
        assertThat(result.getUser()).isSameAs(user);
        assertThat(order.getUserId()).isEqualTo(10L);
        assertThat(order.getUserEmail()).isEqualTo("test@gmail.com");
        assertThat(order.getStatus()).isEqualTo(Status.CREATED);
        assertThat(order.getDeleted()).isFalse();
        assertThat(order.getTotalPrice()).isEqualTo(200L);
        assertThat(order.getOrderItems()).hasSize(1);
        verify(orderRepository).save(order);
    }

    @Test
    void createOrder_shouldThrowInvalidOrderItemsException_whenItemsAreEmpty() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserEmail("test@gmail.com");
        request.setItems(List.of());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InvalidOrderItemsException.class)
                .hasMessage("Order items cannot be empty");

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_shouldThrowItemIdRequiredException_whenItemIdIsNull() {
        CreateOrderRequest request = createOrderRequest("test@gmail.com", null, 1);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ItemIdRequiredException.class)
                .hasMessage("Item id cannot be null");
    }

    @Test
    void createOrder_shouldThrowInvalidOrderItemQuantityException_whenQuantityIsInvalid() {
        CreateOrderRequest request = createOrderRequest("test@gmail.com", 1L, 0);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InvalidOrderItemQuantityException.class)
                .hasMessage("Quantity must be greater than zero");
    }

    @Test
    void createOrder_shouldThrowDuplicateOrderItemException_whenItemIsDuplicated() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserEmail("test@gmail.com");
        request.setItems(List.of(orderItemRequest(1L, 1), orderItemRequest(1L, 2)));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(DuplicateOrderItemException.class)
                .hasMessage("Duplicate item id in order: 1");
    }

    @Test
    void createOrder_shouldThrowItemsNotFoundException_whenSomeItemsDoNotExist() {
        CreateOrderRequest request = createOrderRequest("test@gmail.com", 999L, 1);
        UserResponse user = userResponse(10L, "test@gmail.com");
        Order order = new Order();

        when(userClient.getUserByEmail("test@gmail.com")).thenReturn(user);
        when(orderMapper.toOrder(request)).thenReturn(order);
        when(itemRepository.findAllByIdIn(List.of(999L))).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ItemsNotFoundException.class)
                .hasMessage("Some items were not found");
    }

    @Test
    void getOrderById_shouldReturnOrderResponseWithUserInfo() {
        Order order = order(1L, 10L, "test@gmail.com", Status.CREATED, 100L);
        OrderResponse mappedResponse = new OrderResponse();
        UserResponse user = userResponse(10L, "test@gmail.com");

        when(orderRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toOrderResponse(order)).thenReturn(mappedResponse);
        when(userClient.getUserByEmail("test@gmail.com")).thenReturn(user);

        OrderResponse result = orderService.getOrderById(1L);

        assertThat(result).isSameAs(mappedResponse);
        assertThat(result.getUser()).isSameAs(user);
    }

    @Test
    void getOrderById_shouldThrowOrderNotFoundException_whenOrderDoesNotExist() {
        when(orderRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(1L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order with id 1 not found");
    }

    @Test
    void getOrders_shouldReturnPageOfOrderResponsesWithUserInfo() {
        Order order = order(1L, 10L, "test@gmail.com", Status.CREATED, 100L);
        OrderResponse mappedResponse = new OrderResponse();
        UserResponse user = userResponse(10L, "test@gmail.com");
        Pageable pageable = PageRequest.of(0, 10);
        OrderFilterRequest filterRequest = new OrderFilterRequest();
        filterRequest.setStatuses(List.of(Status.CREATED));

        when(orderRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderMapper.toOrderResponse(order)).thenReturn(mappedResponse);
        when(userClient.getUserByEmail("test@gmail.com")).thenReturn(user);

        Page<OrderResponse> result = orderService.getOrders(filterRequest, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).containsExactly(mappedResponse);
        assertThat(result.getContent().get(0).getUser()).isSameAs(user);
    }

    @Test
    void getOrdersByUserId_shouldReturnListOfOrderResponsesWithUserInfo() {
        Order order = order(1L, 10L, "test@gmail.com", Status.CREATED, 100L);
        OrderResponse mappedResponse = new OrderResponse();
        UserResponse user = userResponse(10L, "test@gmail.com");

        when(orderRepository.findAllByUserIdAndDeletedFalse(10L)).thenReturn(List.of(order));
        when(orderMapper.toOrderResponse(order)).thenReturn(mappedResponse);
        when(userClient.getUserByEmail("test@gmail.com")).thenReturn(user);

        List<OrderResponse> result = orderService.getOrdersByUserId(10L);

        assertThat(result).containsExactly(mappedResponse);
        assertThat(result.get(0).getUser()).isSameAs(user);
    }

    @Test
    void updateOrder_shouldUpdateStatusAndReturnResponseWithUserInfo() {
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus(Status.PAID);
        Order order = order(1L, 10L, "test@gmail.com", Status.CREATED, 100L);
        OrderResponse mappedResponse = new OrderResponse();
        UserResponse user = userResponse(10L, "test@gmail.com");

        when(orderRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toOrderResponse(order)).thenReturn(mappedResponse);
        when(userClient.getUserByEmail("test@gmail.com")).thenReturn(user);

        OrderResponse result = orderService.updateOrder(1L, request);

        assertThat(result).isSameAs(mappedResponse);
        assertThat(result.getUser()).isSameAs(user);
        verify(orderMapper).updateOrderFromRequest(request, order);
        verify(orderRepository).save(order);
    }

    @Test
    void deleteOrderById_shouldSoftDeleteOrder() {
        when(orderRepository.softDeleteById(1L)).thenReturn(1);

        orderService.deleteOrderById(1L);

        verify(orderRepository).softDeleteById(1L);
    }

    @Test
    void deleteOrderById_shouldThrowOrderNotFoundException_whenOrderDoesNotExist() {
        when(orderRepository.softDeleteById(1L)).thenReturn(0);

        assertThatThrownBy(() -> orderService.deleteOrderById(1L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order with id 1 not found");
    }

    private CreateOrderRequest createOrderRequest(String userEmail, Long itemId, Integer quantity) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserEmail(userEmail);
        request.setItems(List.of(orderItemRequest(itemId, quantity)));
        return request;
    }

    private OrderItemRequest orderItemRequest(Long itemId, Integer quantity) {
        OrderItemRequest request = new OrderItemRequest();
        request.setItemId(itemId);
        request.setQuantity(quantity);
        return request;
    }

    private UserResponse userResponse(Long id, String email) {
        UserResponse user = new UserResponse();
        user.setId(id);
        user.setName("Ivan");
        user.setSurname("Ivanov");
        user.setEmail(email);
        user.setActive(true);
        return user;
    }

    private Item item(Long id, String name, Long price) {
        Item item = new Item();
        item.setId(id);
        item.setName(name);
        item.setPrice(price);
        return item;
    }

    private OrderItem orderItem(Integer quantity) {
        OrderItem orderItem = new OrderItem();
        orderItem.setQuantity(quantity);
        return orderItem;
    }

    private Order order(Long id, Long userId, String userEmail, Status status, Long totalPrice) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(userId);
        order.setUserEmail(userEmail);
        order.setStatus(status);
        order.setTotalPrice(totalPrice);
        order.setDeleted(false);
        return order;
    }
}
