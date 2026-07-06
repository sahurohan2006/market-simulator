package com.market.backend.strategy;

import com.market.backend.domain.MarketEvent;
import com.market.backend.domain.Order;
import com.market.backend.domain.Portfolio;
import com.market.backend.domain.Side;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovingAverageCrossoverStrategyTest {
    @Test
    void emitsBuyOnBullishCross() {
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy(2, 3, 10.0);
        Portfolio portfolio = new Portfolio(10_000.0);

        strategy.onEvent(price(100.0, 0), portfolio);
        strategy.onEvent(price(101.0, 1), portfolio);
        List<Order> orders = strategy.onEvent(price(103.0, 2), portfolio);

        assertEquals(1, orders.size());
        assertEquals(Side.BUY, orders.get(0).side());
    }

    private MarketEvent price(double price, long minute) {
        return MarketEvent.price("AAPL", Instant.parse("2024-01-01T09:30:00Z").plusSeconds(minute * 60), price, 100.0);
    }
}
