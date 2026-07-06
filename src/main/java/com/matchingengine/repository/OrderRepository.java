package com.matchingengine.repository;

import com.matchingengine.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findBySymbol(String symbol);
}
