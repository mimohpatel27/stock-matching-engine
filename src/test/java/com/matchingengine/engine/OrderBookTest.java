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
        return order(side, price, qty, "trader-" + side);
    }

    private Order order(OrderSide side, String price, int qty, String traderId) {
        Order o = new Order("TCS", traderId, side, new BigDecimal(price), qty);
        o.setId(++seq); // simulate DB-assigned id
        o.setSequence(seq);
        return o;
    }

    private Order marketOrder(OrderSide side, int qty, String traderId) {
        Order o = new Order("TCS", traderId, side, com.matchingengine.model.OrderType.MARKET, null, qty);
        o.setId(++seq);
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

    @Test
    void tradeRecordsCorrectTraderIdsOnBothSides() {
        Order sell = order(OrderSide.SELL, "490", 100, "trader-alice");
        book.submit(sell);

        Order buy = order(OrderSide.BUY, "500", 100, "trader-bob");
        List<Trade> trades = book.submit(buy);

        assertEquals(1, trades.size());
        assertEquals("trader-bob", trades.get(0).getBuyTraderId());
        assertEquals("trader-alice", trades.get(0).getSellTraderId());
    }

    @Test
    void selfTradeIsSkippedButOrderStillRestsInBook() {
        // Alice has a resting sell. Alice's own buy order should NOT match
        // against it, even though the price crosses.
        Order aliceSell = order(OrderSide.SELL, "490", 100, "alice");
        book.submit(aliceSell);

        Order aliceBuy = order(OrderSide.BUY, "500", 100, "alice");
        List<Trade> trades = book.submit(aliceBuy);

        assertTrue(trades.isEmpty(), "same-trader orders must not match each other");
        // Alice's sell should still be resting in the book, untouched.
        assertEquals(1, book.sellSnapshot(10).size());
        assertEquals(0, aliceSell.getFilledQuantity());
        // Alice's buy should now be resting too, since nothing else matched it.
        assertEquals(1, book.buySnapshot(10).size());
    }

    @Test
    void selfTradeSkipSkipsOverToMatchNextBestPrice() {
        // Alice has a resting sell at 490 (best price).
        // Bob has a resting sell at 495 (worse price).
        // Alice's incoming buy should skip her own 490 sell and match Bob's 495 instead.
        Order aliceSell = order(OrderSide.SELL, "490", 50, "alice");
        book.submit(aliceSell);
        Order bobSell = order(OrderSide.SELL, "495", 50, "bob");
        book.submit(bobSell);

        Order aliceBuy = order(OrderSide.BUY, "500", 50, "alice");
        List<Trade> trades = book.submit(aliceBuy);

        assertEquals(1, trades.size());
        assertEquals(bobSell.getId(), trades.get(0).getSellOrderId(), "should skip alice's own order and match bob's instead");
        // Alice's original sell should still be resting, untouched.
        assertEquals(0, aliceSell.getFilledQuantity());
    }

    @Test
    void marketBuyMatchesAtRestingSellPriceIgnoringOwnPrice() {
        Order sell = order(OrderSide.SELL, "490", 100, "alice");
        book.submit(sell);

        Order marketBuy = marketOrder(OrderSide.BUY, 100, "bob");
        List<Trade> trades = book.submit(marketBuy);

        assertEquals(1, trades.size());
        assertEquals(0, trades.get(0).getPrice().compareTo(new BigDecimal("490")));
        assertEquals(OrderStatus.FILLED, marketBuy.getStatus());
    }

    @Test
    void marketOrderSweepsMultiplePriceLevelsIgnoringPrice() {
        book.submit(order(OrderSide.SELL, "490", 30, "alice"));
        book.submit(order(OrderSide.SELL, "520", 30, "alice"));
        book.submit(order(OrderSide.SELL, "600", 30, "alice"));

        Order marketBuy = marketOrder(OrderSide.BUY, 90, "bob");
        List<Trade> trades = book.submit(marketBuy);

        assertEquals(3, trades.size());
        assertEquals(90, trades.stream().mapToInt(Trade::getQuantity).sum());
        assertEquals(OrderStatus.FILLED, marketBuy.getStatus());
    }

    @Test
    void unfilledMarketOrderIsCancelledNotRested() {
        Order marketBuy = marketOrder(OrderSide.BUY, 100, "bob");
        List<Trade> trades = book.submit(marketBuy);

        assertTrue(trades.isEmpty());
        assertEquals(OrderStatus.CANCELLED, marketBuy.getStatus());
        assertEquals(0, book.buySnapshot(10).size());
    }

    @Test
    void partiallyFilledMarketOrderDoesNotRestRemainder() {
        book.submit(order(OrderSide.SELL, "490", 40, "alice"));

        Order marketBuy = marketOrder(OrderSide.BUY, 100, "bob");
        List<Trade> trades = book.submit(marketBuy);

        assertEquals(1, trades.size());
        assertEquals(40, trades.get(0).getQuantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, marketBuy.getStatus());
        assertEquals(60, marketBuy.remainingQuantity());
        assertEquals(0, book.buySnapshot(10).size());
    }

    @Test
    void marketOrderStillRespectsSelfTradePrevention() {
        Order aliceSell = order(OrderSide.SELL, "490", 100, "alice");
        book.submit(aliceSell);

        Order aliceMarketBuy = marketOrder(OrderSide.BUY, 100, "alice");
        List<Trade> trades = book.submit(aliceMarketBuy);

        assertTrue(trades.isEmpty(), "market order must not self-trade either");
        assertEquals(OrderStatus.CANCELLED, aliceMarketBuy.getStatus());
        assertEquals(1, book.sellSnapshot(10).size());
    }
}
