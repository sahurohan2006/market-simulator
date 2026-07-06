package com.market.backend.book;

import com.market.backend.domain.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookTest {

    @Test
    void restsNonCrossingLimitOrders() {
        OrderBook book = new OrderBook("AAPL");
        List<Fill> fills = book.submitLimitOrder(1, Side.BUY, 99.0, 10.0);

        assertTrue(fills.isEmpty());
        assertEquals(99.0, book.bestBid());
        assertNull(book.bestAsk());
        assertEquals(1, book.bidLevels(5).size());
        assertEquals(10.0, book.bidLevels(5).get(0).totalQuantity());
    }

    @Test
    void matchesCrossingLimitOrderExactly() {
        OrderBook book = new OrderBook("AAPL");
        book.submitLimitOrder(1, Side.SELL, 100.0, 10.0);
        List<Fill> fills = book.submitLimitOrder(2, Side.BUY, 100.0, 10.0);

        assertEquals(1, fills.size());
        Fill fill = fills.get(0);
        assertEquals(1L, fill.makerOrderId());
        assertEquals(2L, fill.takerOrderId());
        assertEquals(100.0, fill.price());
        assertEquals(10.0, fill.quantity());
        assertNull(book.bestAsk());
        assertNull(book.bestBid());
    }

    @Test
    void partiallyFillsTakerAgainstSmallerMaker() {
        OrderBook book = new OrderBook("AAPL");
        book.submitLimitOrder(1, Side.SELL, 100.0, 4.0);
        List<Fill> fills = book.submitLimitOrder(2, Side.BUY, 100.0, 10.0);

        assertEquals(1, fills.size());
        assertEquals(4.0, fills.get(0).quantity());
        // remaining 6 shares of the taker rest on the bid side
        assertEquals(100.0, book.bestBid());
        assertEquals(6.0, book.bidLevels(1).get(0).totalQuantity());
        assertNull(book.bestAsk());
    }

    @Test
    void enforcesPriceTimePriorityWithinALevel() {
        OrderBook book = new OrderBook("AAPL");
        book.submitLimitOrder(1, Side.SELL, 100.0, 5.0);
        book.submitLimitOrder(2, Side.SELL, 100.0, 5.0);

        List<Fill> fills = book.submitLimitOrder(3, Side.BUY, 100.0, 5.0);

        assertEquals(1, fills.size());
        assertEquals(1L, fills.get(0).makerOrderId(), "earlier resting order at the same price must fill first");
    }

    @Test
    void sweepsMultiplePriceLevelsWithMarketOrder() {
        OrderBook book = new OrderBook("AAPL");
        book.submitLimitOrder(1, Side.SELL, 100.0, 5.0);
        book.submitLimitOrder(2, Side.SELL, 101.0, 5.0);
        book.submitLimitOrder(3, Side.SELL, 102.0, 5.0);

        List<Fill> fills = book.submitMarketOrder(4, Side.BUY, 12.0);

        assertEquals(3, fills.size());
        assertEquals(100.0, fills.get(0).price());
        assertEquals(101.0, fills.get(1).price());
        assertEquals(102.0, fills.get(2).price());
        assertEquals(5.0, fills.get(0).quantity());
        assertEquals(5.0, fills.get(1).quantity());
        assertEquals(2.0, fills.get(2).quantity());
        // remaining quantity at the top level after the sweep
        assertEquals(3.0, book.askLevels(1).get(0).totalQuantity());
    }

    @Test
    void marketOrderDropsUnfilledRemainderInsteadOfResting() {
        OrderBook book = new OrderBook("AAPL");
        book.submitLimitOrder(1, Side.SELL, 100.0, 3.0);

        List<Fill> fills = book.submitMarketOrder(2, Side.BUY, 10.0);

        assertEquals(1, fills.size());
        assertEquals(3.0, fills.get(0).quantity());
        assertNull(book.bestAsk());
        assertNull(book.bestBid(), "unfilled market order remainder must not rest in the book");
    }

    @Test
    void cancelRemovesRestingOrder() {
        OrderBook book = new OrderBook("AAPL");
        book.submitLimitOrder(1, Side.BUY, 99.0, 10.0);

        assertTrue(book.cancel(1));
        assertNull(book.bestBid());
        assertFalse(book.cancel(1), "cancelling an already-removed order returns false");
    }

    @Test
    void cancelledOrderCannotBeMatched() {
        OrderBook book = new OrderBook("AAPL");
        book.submitLimitOrder(1, Side.BUY, 99.0, 10.0);
        book.cancel(1);

        List<Fill> fills = book.submitLimitOrder(2, Side.SELL, 99.0, 10.0);
        assertTrue(fills.isEmpty());
        assertEquals(99.0, book.bestAsk());
    }

    @Test
    void reportsSpreadAndMidPrice() {
        OrderBook book = new OrderBook("AAPL");
        book.submitLimitOrder(1, Side.BUY, 99.0, 10.0);
        book.submitLimitOrder(2, Side.SELL, 101.0, 10.0);

        assertEquals(2.0, book.spread());
        assertEquals(100.0, book.midPrice());
    }

    @Test
    void rejectsNonPositiveQuantity() {
        OrderBook book = new OrderBook("AAPL");
        assertThrows(IllegalArgumentException.class, () -> book.submitLimitOrder(1, Side.BUY, 100.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> book.submitMarketOrder(2, Side.BUY, -1.0));
    }
}
