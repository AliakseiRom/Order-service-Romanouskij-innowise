package com.innowise.orderservice.repository;

import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.utils.Status;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
    Optional<Order> findByIdAndDeletedFalse(Long id);

    @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
    List<Order> findAllByUserIdAndDeletedFalse(Long userId);

    List<Order> findAllByStatusInAndDeletedFalse(Collection<Status> statuses);

    boolean existsByIdAndDeletedFalse(Long id);

    @Query(
            value = """
                    UPDATE orders
                    SET deleted = TRUE,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = :id
                      AND deleted = FALSE
                    """,
            nativeQuery = true
    )
    int softDeleteById(@Param("id") Long id);
}