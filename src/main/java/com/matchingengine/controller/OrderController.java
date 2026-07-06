package com.matchingengine.controller;

import com.matchingengine.dto.OrderBookSnapshotResponse;
import com.matchingengine.dto.OrderRequest;
import com.matchingengine.dto.OrderResponse;
import com.matchingengine.engine.MatchingEngine;
import com.matchingengine.engine.OrderBook;
import com.matchingengine.model.Order;
import com.matchingengine.model.Trade;
import com.matchingengine.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;
    private final MatchingEngine matchingEngine;

    public OrderController(OrderService orderService, MatchingEngine matchingEngine) {
        this.orderService = orderService;
        this.matchingEngine = matchingEngine;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @DeleteMapping("/orders/{symbol}/{id}")
    public ResponseEntity<Void> cancelOrder(@PathVariable String symbol, @PathVariable Long id) {
        orderService.cancelOrder(symbol, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders/symbol/{symbol}")
    public ResponseEntity<List<Order>> getOrdersForSymbol(@PathVariable String symbol) {
        return ResponseEntity.ok(orderService.getOrdersForSymbol(symbol));
    }

    @GetMapping("/orderbook/{symbol}")
    public ResponseEntity<OrderBookSnapshotResponse> getOrderBook(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10") int depth) {
        OrderBook book = matchingEngine.getBook(symbol.toUpperCase());
        if (book == null) {
            return ResponseEntity.ok(new OrderBookSnapshotResponse(symbol.toUpperCase(), Collections.emptyList(), Collections.emptyList()));
        }
        return ResponseEntity.ok(new OrderBookSnapshotResponse(
                symbol.toUpperCase(),
                book.buySnapshot(depth),
                book.sellSnapshot(depth)
        ));
    }

    @GetMapping("/trades/{symbol}")
    public ResponseEntity<List<Trade>> getTrades(@PathVariable String symbol) {
        return ResponseEntity.ok(orderService.getTradesForSymbol(symbol));
    }
}
