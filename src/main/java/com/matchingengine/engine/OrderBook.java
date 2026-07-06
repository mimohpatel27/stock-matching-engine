package com.matchingengine.engine;

import com.matchingengine.model.Order;
import com.matchingengine.model.OrderSide;
import com.matchingengine.model.OrderStatus;
import com.matchingengine.model.Trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A single-symbol order book implementing price-time priority matching.
 *
 * DSA core:
 *  - buyHeap: MAX-HEAP on price (highest buy price gets matched first),
 *             ties broken by earliest sequence (FIFO).
 *  - sellHeap: MIN-HEAP on price (lowest sell price gets matched first),
 *              ties broken by earliest sequence (FIFO).
 *
 * This class is NOT thread-safe by itself; callers (MatchingEngine) are
 * responsible for synchronizing access per symbol.
 */
public class OrderBook {

    private final String symbol;

    // Max-heap: highest price first, then oldest first
    private final PriorityQueue<Order> buyHeap = new PriorityQueue<>(
            Comparator.comparing(Order::getPrice, Comparator.reverseOrder())
                    .thenComparing(Order::getSequence)
    );

    // Min-heap: lowest price first, then oldest first
    private final PriorityQueue<Order> sellHeap = new PriorityQueue<>(
            Comparator.comparing(Order::getPrice)
                    .thenComparing(Order::getSequence)
    );

    // Lets us cancel an order in O(log n) without scanning the heap.
    private final Map<Long, Order> openOrdersById = new HashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Submits a new order into the book and attempts to match it immediately
     * against the resting opposite-side orders. Returns the list of trades
     * generated (may be empty if nothing matched).
     *
     * Any unfilled remainder rests in the book (unless it's a pure market
     * sweep with no remaining liquidity to match against, in which case it
     * still rests as a limit order at its own price).
     */
    public List<Trade> submit(Order incoming) {
        List<Trade> trades = new ArrayList<>();

        if (incoming.getSide() == OrderSide.BUY) {
            matchBuy(incoming, trades);
            if (incoming.remainingQuantity() > 0) {
                buyHeap.offer(incoming);
                openOrdersById.put(incoming.getId(), incoming);
            }
        } else {
            matchSell(incoming, trades);
            if (incoming.remainingQuantity() > 0) {
                sellHeap.offer(incoming);
                openOrdersById.put(incoming.getId(), incoming);
            }
        }

        return trades;
    }

    private void matchBuy(Order buyOrder, List<Trade> trades) {
        // Keep matching while the incoming buy order still wants quantity
        // AND the best resting sell price is <= the buy price.
        while (buyOrder.remainingQuantity() > 0
                && !sellHeap.isEmpty()
                && sellHeap.peek().getPrice().compareTo(buyOrder.getPrice()) <= 0) {

            Order bestSell = sellHeap.poll();
            openOrdersById.remove(bestSell.getId());

            int tradeQty = Math.min(buyOrder.remainingQuantity(), bestSell.remainingQuantity());
            // Execution price convention: the resting order's price (price-time priority standard)
            var tradePrice = bestSell.getPrice();

            applyFill(buyOrder, tradeQty);
            applyFill(bestSell, tradeQty);

            trades.add(new Trade(symbol, buyOrder.getId(), bestSell.getId(), tradePrice, tradeQty));

            // If the resting sell order still has quantity left, put it back
            if (bestSell.remainingQuantity() > 0) {
                sellHeap.offer(bestSell);
                openOrdersById.put(bestSell.getId(), bestSell);
            }
        }
    }

    private void matchSell(Order sellOrder, List<Trade> trades) {
        while (sellOrder.remainingQuantity() > 0
                && !buyHeap.isEmpty()
                && buyHeap.peek().getPrice().compareTo(sellOrder.getPrice()) >= 0) {

            Order bestBuy = buyHeap.poll();
            openOrdersById.remove(bestBuy.getId());

            int tradeQty = Math.min(sellOrder.remainingQuantity(), bestBuy.remainingQuantity());
            var tradePrice = bestBuy.getPrice();

            applyFill(sellOrder, tradeQty);
            applyFill(bestBuy, tradeQty);

            trades.add(new Trade(symbol, bestBuy.getId(), sellOrder.getId(), tradePrice, tradeQty));

            if (bestBuy.remainingQuantity() > 0) {
                buyHeap.offer(bestBuy);
                openOrdersById.put(bestBuy.getId(), bestBuy);
            }
        }
    }

    private void applyFill(Order order, int qty) {
        order.setFilledQuantity(order.getFilledQuantity() + qty);
        order.setStatus(order.isFullyFilled() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED);
    }

    /**
     * Cancels a resting order. Returns true if it was found and cancelled.
     * O(log n) removal from the heap (PriorityQueue.remove is O(n) actually -
     * see note below).
     */
    public boolean cancel(Long orderId) {
        Order order = openOrdersById.remove(orderId);
        if (order == null) {
            return false;
        }
        order.setStatus(OrderStatus.CANCELLED);
        // Note: java.util.PriorityQueue.remove(Object) is O(n) because it
        // scans internally. For a resume-grade project this is an honest
        // limitation to call out; a production book would use an
        // indexed/binary heap or TreeMap<price, Deque<Order>> to get O(log n)
        // cancellation. Left as a documented improvement.
        if (order.getSide() == OrderSide.BUY) {
            buyHeap.remove(order);
        } else {
            sellHeap.remove(order);
        }
        return true;
    }

    /** Top N price levels aggregated, best price first, for a book snapshot. */
    public List<PriceLevel> buySnapshot(int depth) {
        return aggregate(buyHeap, depth, true);
    }

    public List<PriceLevel> sellSnapshot(int depth) {
        return aggregate(sellHeap, depth, false);
    }

    private List<PriceLevel> aggregate(PriorityQueue<Order> heap, int depth, boolean descending) {
        Map<java.math.BigDecimal, Integer> byPrice = new HashMap<>();
        for (Order o : heap) {
            byPrice.merge(o.getPrice(), o.remainingQuantity(), Integer::sum);
        }
        Comparator<java.math.BigDecimal> cmp = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return byPrice.entrySet().stream()
                .sorted((a, b) -> cmp.compare(a.getKey(), b.getKey()))
                .limit(depth)
                .map(e -> new PriceLevel(e.getKey(), e.getValue()))
                .toList();
    }

    public record PriceLevel(java.math.BigDecimal price, int totalQuantity) {
    }
}
