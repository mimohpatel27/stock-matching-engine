package com.matchingengine.engine;

import com.matchingengine.model.Order;
import com.matchingengine.model.OrderSide;
import com.matchingengine.model.OrderStatus;
import com.matchingengine.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private OrderBook book;
    private long seq;

    @BeforeEach
    void setUp() {
        book = new OrderBook("TCS");
        seq = 0;
    }

    private Order order(OrderSide side, String price, int qty) {
        Order o = new Order("TCS", side, new BigDecimal(price), qty);
        o.setId(++seq); // simulate DB-assigned id
        o.setSequence(seq);
        return o;
    }

    @Test
    void exactPriceMatchFillsBothOrdersCompletely() {
        Order sell = order(OrderSide.SELL, "490", 100);
        book.submit(sell);

        Order buy = order(OrderSide.BUY, "500", 100);
        List<Trade> trades = book.submit(buy);

        assertEquals(1, trades.size());
        Trade t = trades.get(0);
        assertEquals(0, t.getPrice().compareTo(new BigDecimal("490"))); // resting price wins
        assertEquals(100, t.getQuantity());
        assertEquals(OrderStatus.FILLED, buy.getStatus());
        assertEquals(OrderStatus.FILLED, sell.getStatus());
    }

    @Test
    void partialFillLeavesRemainderResting() {
        Order sell = order(OrderSide.SELL, "490", 60);
        book.submit(sell);

        Order buy = order(OrderSide.BUY, "500", 100);
        List<Trade> trades = book.submit(buy);

        assertEquals(1, trades.size());
        assertEquals(60, trades.get(0).getQuantity());
        assertEquals(OrderStatus.FILLED, sell.getStatus());
        assertEquals(OrderStatus.PARTIALLY_FILLED, buy.getStatus());
        assertEquals(40, buy.remainingQuantity());
    }

    @Test
    void noMatchWhenPricesDoNotCross() {
        book.submit(order(OrderSide.SELL, "510", 100));
        List<Trade> trades = book.submit(order(OrderSide.BUY, "500", 100));

        assertTrue(trades.isEmpty());
    }

    @Test
    void priceTimePriority_bestPriceMatchedFirst() {
        // Two resting sells; the cheaper one should match first even though
        // it was submitted second.
        Order expensiveSell = order(OrderSide.SELL, "495", 50);
        book.submit(expensiveSell);
        Order cheapSell = order(OrderSide.SELL, "490", 50);
        book.submit(cheapSell);

        Order buy = order(OrderSide.BUY, "500", 50);
        List<Trade> trades = book.submit(buy);

        assertEquals(1, trades.size());
        assertEquals(cheapSell.getId(), trades.get(0).getSellOrderId());
    }

    @Test
    void priceTimePriority_earlierOrderMatchedFirstAtSamePrice() {
        Order first = order(OrderSide.SELL, "490", 50);
        book.submit(first);
        Order second = order(OrderSide.SELL, "490", 50);
        book.submit(second);

        Order buy = order(OrderSide.BUY, "500", 50);
        List<Trade> trades = book.submit(buy);

        assertEquals(1, trades.size());
        assertEquals(first.getId(), trades.get(0).getSellOrderId(), "FIFO: earlier order at same price fills first");
    }

    @Test
    void largeBuySweepsMultipleSellLevels() {
        book.submit(order(OrderSide.SELL, "490", 30));
        book.submit(order(OrderSide.SELL, "495", 30));
        book.submit(order(OrderSide.SELL, "500", 30));

        Order buy = order(OrderSide.BUY, "500", 90);
        List<Trade> trades = book.submit(buy);

        assertEquals(3, trades.size());
        assertEquals(90, trades.stream().mapToInt(Trade::getQuantity).sum());
        assertEquals(OrderStatus.FILLED, buy.getStatus());
    }

    @Test
    void cancelRemovesRestingOrderFromBook() {
        Order sell = order(OrderSide.SELL, "490", 100);
        book.submit(sell);

        assertTrue(book.cancel(sell.getId()));
        assertEquals(0, book.sellSnapshot(10).size());

        // A buy that would have matched now finds nothing
        List<Trade> trades = book.submit(order(OrderSide.BUY, "500", 100));
        assertTrue(trades.isEmpty());
    }

    @Test
    void cancelNonexistentOrderReturnsFalse() {
        assertFalse(book.cancel(999L));
    }
}
