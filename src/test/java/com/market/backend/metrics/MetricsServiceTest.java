package com.market.backend.metrics;

import com.market.backend.domain.EquityPoint;
import com.market.backend.domain.Side;
import com.market.backend.domain.Trade;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsServiceTest {
    @Test
    void calculatesRiskAndTradeMetrics() {
        MetricsService service = new MetricsService();
        List<EquityPoint> curve = List.of(
                new EquityPoint(Instant.parse("2024-01-01T00:00:00Z"), 100.0),
                new EquityPoint(Instant.parse("2024-01-02T00:00:00Z"), 110.0),
                new EquityPoint(Instant.parse("2024-01-03T00:00:00Z"), 90.0),
                new EquityPoint(Instant.parse("2024-01-04T00:00:00Z"), 120.0));
        List<Trade> trades = List.of(
                new Trade(Instant.parse("2024-01-01T00:00:00Z"), "AAPL", Side.BUY, 1, 100, 100, 0, 0, 900, 1),
                new Trade(Instant.parse("2024-01-02T00:00:00Z"), "AAPL", Side.SELL, 1, 105, 105, 0, 0, 1_005, 0));

        RiskMetrics metrics = service.calculate(curve, 100.0, trades);

        assertEquals(20.0, metrics.pnl(), 0.0001);
        assertEquals(20.0, metrics.maxDrawdown(), 0.0001);
        assertEquals(1.0, metrics.winRate(), 0.0001);
        assertEquals(5.0, metrics.averageTradePnl(), 0.0001);
        assertEquals(205.0, metrics.turnover(), 0.0001);
        assertTrue(metrics.annualizedVolatility() > 0.0);
    }
}
