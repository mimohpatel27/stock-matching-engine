package com.matchingengine.dto;

import com.matchingengine.model.OrderSide;
import com.matchingengine.model.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class OrderRequest {

    @NotNull
    private String symbol;

    @NotBlank(message = "traderId is required")
    private String traderId;

    @NotNull
    private OrderSide side;

    // Defaults to LIMIT if not specified.
    private OrderType orderType = OrderType.LIMIT;

    // Required for LIMIT orders; ignored for MARKET orders (validated in
    // OrderService since it's conditional on orderType).
    @DecimalMin(value = "0.01", message = "price must be positive")
    private BigDecimal price;

    @Min(value = 1, message = "quantity must be at least 1")
    private int quantity;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getTraderId() {
        return traderId;
    }

    public void setTraderId(String traderId) {
        this.traderId = traderId;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
