package com.innowise.orderservice.repository;

import com.innowise.orderservice.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findAllByIdIn(Collection<Long> ids);

    boolean existsByNameIgnoreCase(String name);
}