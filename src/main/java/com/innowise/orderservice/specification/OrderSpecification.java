package com.innowise.orderservice.specification;

import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.utils.Status;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collection;

public final class OrderSpecification {

    private OrderSpecification() {
    }

    public static Specification<Order> withFilters(
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Collection<Status> statuses
    ) {
        return notDeleted()
                .and(createdAtFrom(createdFrom))
                .and(createdAtTo(createdTo))
                .and(statusIn(statuses));
    }

    public static Specification<Order> withFiltersAndUserId(
            Long userId,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            Collection<Status> statuses
    ) {
        return notDeleted()
                .and(userIdEquals(userId))
                .and(createdAtFrom(createdFrom))
                .and(createdAtTo(createdTo))
                .and(statusIn(statuses));
    }

    public static Specification<Order> notDeleted() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.isFalse(root.get("deleted"));
    }

    public static Specification<Order> userIdEquals(Long userId) {
        return (root, query, criteriaBuilder) -> {
            if (userId == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(root.get("userId"), userId);
        };
    }

    public static Specification<Order> createdAtFrom(LocalDateTime createdFrom) {
        return (root, query, criteriaBuilder) -> {
            if (createdFrom == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom);
        };
    }

    public static Specification<Order> createdAtTo(LocalDateTime createdTo) {
        return (root, query, criteriaBuilder) -> {
            if (createdTo == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), createdTo);
        };
    }

    public static Specification<Order> statusIn(Collection<Status> statuses) {
        return (root, query, criteriaBuilder) -> {
            if (statuses == null || statuses.isEmpty()) {
                return criteriaBuilder.conjunction();
            }

            return root.get("status").in(statuses);
        };
    }
}