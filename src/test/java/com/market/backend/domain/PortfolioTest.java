package com.market.backend.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortfolioTest {
    @Test
    void appliesCommissionAndSlippageToFills() {
        Portfolio portfolio = new Portfolio(1_000.0, new ExecutionConfig(0.01, 10.0, false, 100.0));
        portfolio.execute(
                MarketEvent.price("AAPL", Instant.parse("2024-01-01T00:00:00Z"), 100.0, 1_000.0),
                new Order("AAPL", Side.BUY, 2.0));

        Trade trade = portfolio.trades().get(0);
        assertEquals(100.10, trade.price(), 0.0001);
        assertEquals(0.02, trade.commission(), 0.0001);
        assertEquals(799.78, portfolio.cash(), 0.0001);
    }

    @Test
    void blocksShortSaleWhenShortingDisabled() {
        Portfolio portfolio = new Portfolio(1_000.0, new ExecutionConfig(0.0, 0.0, false, 100.0));
        portfolio.execute(
                MarketEvent.price("AAPL", Instant.parse("2024-01-01T00:00:00Z"), 100.0, 1_000.0),
                new Order("AAPL", Side.SELL, 1.0));

        assertEquals(0, portfolio.trades().size());
        assertEquals(0.0, portfolio.position("AAPL"), 0.0001);
    }

    @Test
    void enforcesMaxAbsolutePosition() {
        Portfolio portfolio = new Portfolio(10_000.0, new ExecutionConfig(0.0, 0.0, false, 5.0));
        portfolio.execute(
                MarketEvent.price("AAPL", Instant.parse("2024-01-01T00:00:00Z"), 100.0, 1_000.0),
                new Order("AAPL", Side.BUY, 10.0));

        assertEquals(0, portfolio.trades().size());
    }
}
