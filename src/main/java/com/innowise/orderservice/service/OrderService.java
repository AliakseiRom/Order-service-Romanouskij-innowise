package com.innowise.orderservice.service;

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
import com.innowise.orderservice.kafka.event.CreatePaymentEvent;
import com.innowise.orderservice.mapper.OrderItemMapper;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.specification.OrderSpecification;
import com.innowise.orderservice.utils.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserClient userClient;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        validateOrderItems(request.getItems());

        UserResponse user = userClient.getUserByEmail(request.getUserEmail());

        Order order = orderMapper.toOrder(request);
        order.setUserId(user.getId());
        order.setUserEmail(user.getEmail());
        order.setStatus(Status.CREATED);
        order.setDeleted(false);

        addOrderItems(order, request.getItems());

        order.setTotalPrice(calculateTotalPrice(order));

        Order savedOrder = orderRepository.save(order);

        OrderResponse response = orderMapper.toOrderResponse(savedOrder);
        response.setUser(user);

        return response;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = findOrderById(id);

        return buildOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(OrderFilterRequest filterRequest, Pageable pageable) {
        LocalDateTime createdFrom = filterRequest == null ? null : filterRequest.getCreatedFrom();
        LocalDateTime createdTo = filterRequest == null ? null : filterRequest.getCreatedTo();
        List<Status> statuses = filterRequest == null ? null : filterRequest.getStatuses();

        Specification<Order> specification = OrderSpecification.withFilters(
                createdFrom,
                createdTo,
                statuses
        );

        Page<Order> orders = orderRepository.findAll(specification, pageable);

        return orders.map(this::buildOrderResponse);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        List<Order> orders = orderRepository.findAllByUserIdAndDeletedFalse(userId);

        return orders.stream()
                .map(this::buildOrderResponse)
                .toList();
    }

    @Transactional
    public OrderResponse updateOrder(Long id, UpdateOrderRequest request) {
        Order order = findOrderById(id);

        orderMapper.updateOrderFromRequest(request, order);

        if (request.getItems() != null) {
            validateOrderItems(request.getItems());

            order.getOrderItems().clear();

            addOrderItems(order, request.getItems());

            order.setTotalPrice(calculateTotalPrice(order));
        }

        Order updatedOrder = orderRepository.save(order);

        return buildOrderResponse(updatedOrder);
    }

    @Transactional
    public void deleteOrderById(Long id) {
        int updatedRows = orderRepository.softDeleteById(id);

        if (updatedRows == 0) {
            throw new OrderNotFoundException("Order with id " + id + " not found");
        }
    }

    private Order findOrderById(Long id) {
        return orderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new OrderNotFoundException("Order with id " + id + " not found"));
    }

    private OrderResponse buildOrderResponse(Order order) {
        OrderResponse response = orderMapper.toOrderResponse(order);

        UserResponse user = userClient.getUserByEmail(order.getUserEmail());
        response.setUser(user);

        return response;
    }

    private void addOrderItems(Order order, List<OrderItemRequest> orderItemRequests) {
        List<Long> itemIds = orderItemRequests.stream()
                .map(OrderItemRequest::getItemId)
                .distinct()
                .toList();

        List<Item> items = itemRepository.findAllByIdIn(itemIds);

        if (items.size() != itemIds.size()) {
            throw new ItemsNotFoundException("Some items were not found");
        }

        Map<Long, Item> itemsById = items.stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        for (OrderItemRequest orderItemRequest : orderItemRequests) {
            Item item = itemsById.get(orderItemRequest.getItemId());

            OrderItem orderItem = orderItemMapper.toOrderItem(orderItemRequest);
            orderItem.setItem(item);

            order.addOrderItem(orderItem);
        }
    }

    private Long calculateTotalPrice(Order order) {
        return order.getOrderItems()
                .stream()
                .mapToLong(orderItem -> orderItem.getItem().getPrice() * orderItem.getQuantity())
                .sum();
    }

    private void validateOrderItems(Collection<OrderItemRequest> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            throw new InvalidOrderItemsException("Order items cannot be empty");
        }

        Set<Long> itemIds = new HashSet<>();

        for (OrderItemRequest orderItem : orderItems) {
            if (orderItem.getItemId() == null) {
                throw new ItemIdRequiredException("Item id cannot be null");
            }

            if (orderItem.getQuantity() == null || orderItem.getQuantity() <= 0) {
                throw new InvalidOrderItemQuantityException("Quantity must be greater than zero");
            }

            if (!itemIds.add(orderItem.getItemId())) {
                throw new DuplicateOrderItemException("Duplicate item id in order: " + orderItem.getItemId());
            }
        }
    }

    @Transactional
    public void handleCreatePaymentEvent(CreatePaymentEvent event) {
        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if ("SUCCESS".equals(event.getPaymentStatus())) {
            order.setStatus(Status.PAID);
        } else if ("FAILED".equals(event.getPaymentStatus())) {
            order.setStatus(Status.CANCELLED);
        }

        orderRepository.save(order);
    }
}