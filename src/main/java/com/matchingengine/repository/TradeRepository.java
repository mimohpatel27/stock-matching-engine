package com.matchingengine.repository;

import com.matchingengine.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findBySymbolOrderByExecutedAtDesc(String symbol);
}
