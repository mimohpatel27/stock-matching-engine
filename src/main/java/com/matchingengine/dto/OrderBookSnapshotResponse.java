package com.matchingengine.dto;

import com.matchingengine.engine.OrderBook;

import java.util.List;

public class OrderBookSnapshotResponse {
    private String symbol;
    private List<OrderBook.PriceLevel> bids;
    private List<OrderBook.PriceLevel> asks;

    public OrderBookSnapshotResponse(String symbol, List<OrderBook.PriceLevel> bids, List<OrderBook.PriceLevel> asks) {
        this.symbol = symbol;
        this.bids = bids;
        this.asks = asks;
    }

    public String getSymbol() {
        return symbol;
    }

    public List<OrderBook.PriceLevel> getBids() {
        return bids;
    }

    public List<OrderBook.PriceLevel> getAsks() {
        return asks;
    }
}
