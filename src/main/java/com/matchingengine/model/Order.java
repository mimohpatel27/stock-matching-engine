package com.matchingengine.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String traderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType = OrderType.LIMIT;

    // Nullable because MARKET orders don't specify a price — they match at
    // whatever price is available in the book.
    @Column(precision = 19, scale = 4)
    private BigDecimal price;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int filledQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.OPEN;

    // Used for price-time priority. Also used as the tie-breaker within a
    // price level so FIFO order is preserved inside the heap.
    @Column(nullable = false)
    private long sequence;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Order() {
    }

    public Order(String symbol, String traderId, OrderSide side, BigDecimal price, int quantity) {
        this(symbol, traderId, side, OrderType.LIMIT, price, quantity);
    }

    public Order(String symbol, String traderId, OrderSide side, OrderType orderType, BigDecimal price, int quantity) {
        this.symbol = symbol;
        this.traderId = traderId;
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.quantity = quantity;
    }

    public int remainingQuantity() {
        return quantity - filledQuantity;
    }

    public boolean isFullyFilled() {
        return filledQuantity >= quantity;
    }

    // --- getters / setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public int getFilledQuantity() {
        return filledQuantity;
    }

    public void setFilledQuantity(int filledQuantity) {
        this.filledQuantity = filledQuantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
