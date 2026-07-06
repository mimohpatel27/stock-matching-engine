package com.matchingengine.engine;

import com.matchingengine.model.Order;
import com.matchingengine.model.Trade;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds one OrderBook per symbol and routes incoming orders to the correct
 * book. Concurrency model: a dedicated lock per symbol, so orders on
 * different symbols (e.g. INFY vs TCS) can be matched in parallel, while
 * orders on the SAME symbol are strictly serialized (required for price-time
 * priority to be meaningful).
 */
@Component
public class MatchingEngine {

    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong sequenceGenerator = new AtomicLong(0);

    public List<Trade> submitOrder(Order order) {
        order.setSequence(sequenceGenerator.incrementAndGet());

        String symbol = order.getSymbol();
        OrderBook book = books.computeIfAbsent(symbol, OrderBook::new);
        ReentrantLock lock = locks.computeIfAbsent(symbol, s -> new ReentrantLock());

        lock.lock();
        try {
            return book.submit(order);
        } finally {
            lock.unlock();
        }
    }

    public boolean cancelOrder(String symbol, Long orderId) {
        OrderBook book = books.get(symbol);
        if (book == null) {
            return false;
        }
        ReentrantLock lock = locks.computeIfAbsent(symbol, s -> new ReentrantLock());
        lock.lock();
        try {
            return book.cancel(orderId);
        } finally {
            lock.unlock();
        }
    }

    public OrderBook getBook(String symbol) {
        return books.get(symbol);
    }
}
