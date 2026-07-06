package com.matchingengine.dto;

import com.matchingengine.model.Order;
import com.matchingengine.model.OrderSide;
import com.matchingengine.model.OrderStatus;
import com.matchingengine.model.Trade;

import java.math.BigDecimal;
import java.util.List;

public class OrderResponse {

    private Long orderId;
    private String symbol;
    private OrderSide side;
    private BigDecimal price;
    private int quantity;
    private int filledQuantity;
    private OrderStatus status;
    private List<Trade> trades;

    public static OrderResponse from(Order order, List<Trade> trades) {
        OrderResponse r = new OrderResponse();
        r.orderId = order.getId();
        r.symbol = order.getSymbol();
        r.side = order.getSide();
        r.price = order.getPrice();
        r.quantity = order.getQuantity();
        r.filledQuantity = order.getFilledQuantity();
        r.status = order.getStatus();
        r.trades = trades;
        return r;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getFilledQuantity() {
        return filledQuantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<Trade> getTrades() {
        return trades;
    }
}
