package com.matchingengine.service;

import com.matchingengine.dto.OrderRequest;
import com.matchingengine.dto.OrderResponse;
import com.matchingengine.engine.MatchingEngine;
import com.matchingengine.exception.OrderNotFoundException;
import com.matchingengine.model.Order;
import com.matchingengine.model.OrderStatus;
import com.matchingengine.model.OrderType;
import com.matchingengine.model.Trade;
import com.matchingengine.repository.OrderRepository;
import com.matchingengine.repository.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final MatchingEngine matchingEngine;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;

    public OrderService(MatchingEngine matchingEngine, OrderRepository orderRepository, TradeRepository tradeRepository) {
        this.matchingEngine = matchingEngine;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
    }

    /**
     * Places a new order:
     *  1. Persist it (so it has an ID and a DB record even before matching)
     *  2. Feed it to the in-memory matching engine
     *  3. Persist any resulting trades and update filled quantities/status
     */
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        OrderType orderType = request.getOrderType() == null ? OrderType.LIMIT : request.getOrderType();

        if (orderType == OrderType.LIMIT && request.getPrice() == null) {
            throw new IllegalArgumentException("price is required for LIMIT orders");
        }
        // MARKET orders ignore any price the client might have sent — they
        // match at whatever price is available in the book.
        BigDecimal effectivePrice = orderType == OrderType.MARKET ? null : request.getPrice();

        Order order = new Order(
                request.getSymbol().toUpperCase(),
                request.getTraderId(),
                request.getSide(),
                orderType,
                effectivePrice,
                request.getQuantity()
        );
        order = orderRepository.save(order); // gets an ID

        List<Trade> trades = matchingEngine.submitOrder(order);

        // order object was mutated in place by the engine (filledQuantity/status)
        orderRepository.save(order);
        if (!trades.isEmpty()) {
            tradeRepository.saveAll(trades);
            // Update the resting counter-party orders' persisted state too.
            for (Trade t : trades) {
                Long counterpartyId = order.getSide().name().equals("BUY") ? t.getSellOrderId() : t.getBuyOrderId();
                orderRepository.findById(counterpartyId).ifPresent(counterparty -> {
                    // Recompute from the live in-memory order isn't directly accessible here,
                    // so we increment based on the trade quantity (safe since trades are the
                    // single source of truth for fills).
                    counterparty.setFilledQuantity(counterparty.getFilledQuantity() + t.getQuantity());
                    counterparty.setStatus(counterparty.isFullyFilled() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED);
                    orderRepository.save(counterparty);
                });
            }
        }

        return OrderResponse.from(order, trades);
    }

    @Transactional
    public void cancelOrder(String symbol, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        boolean removedFromBook = matchingEngine.cancelOrder(symbol.toUpperCase(), orderId);
        if (!removedFromBook && order.getStatus() != OrderStatus.OPEN && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            throw new IllegalStateException("Order " + orderId + " cannot be cancelled (status=" + order.getStatus() + ")");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public List<Order> getOrdersForSymbol(String symbol) {
        return orderRepository.findBySymbol(symbol.toUpperCase());
    }

    public List<Trade> getTradesForSymbol(String symbol) {
        return tradeRepository.findBySymbolOrderByExecutedAtDesc(symbol.toUpperCase());
    }
}
