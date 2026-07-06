package com.market.backend;

import com.market.backend.csv.CsvMarketEventReader;
import com.market.backend.domain.BacktestResult;
import com.market.backend.domain.EquityPoint;
import com.market.backend.engine.BacktestEngine;
import com.market.backend.metrics.MetricsService;
import com.market.backend.metrics.RiskMetrics;
import com.market.backend.strategy.MovingAverageCrossoverStrategy;

import java.nio.file.Path;
import java.util.List;

public final class MarketSimulatorSmokeTest {
    public static void main(String[] args) throws Exception {
        metricsCalculatePnlAndDrawdown();
        csvReplayProducesTrades();
        System.out.println("Smoke tests passed");
    }

    private static void metricsCalculatePnlAndDrawdown() {
        MetricsService service = new MetricsService();
        RiskMetrics metrics = service.calculate(List.of(
                new EquityPoint(java.time.Instant.parse("2024-01-01T00:00:00Z"), 100.0),
                new EquityPoint(java.time.Instant.parse("2024-01-02T00:00:00Z"), 110.0),
                new EquityPoint(java.time.Instant.parse("2024-01-03T00:00:00Z"), 90.0),
                new EquityPoint(java.time.Instant.parse("2024-01-04T00:00:00Z"), 120.0)), 100.0);
        assertClose(metrics.pnl(), 20.0, "pnl");
        assertClose(metrics.maxDrawdown(), 20.0, "maxDrawdown");
    }

    private static void csvReplayProducesTrades() throws Exception {
        try (CsvMarketEventReader reader = new CsvMarketEventReader(Path.of("data/sample-prices.csv"))) {
            BacktestResult result = new BacktestEngine(new MetricsService())
                    .run(reader, new MovingAverageCrossoverStrategy(2, 3, 10.0), 10_000.0);
            if (result.eventsProcessed() != 8) {
                throw new AssertionError("Expected 8 events, got " + result.eventsProcessed());
            }
            if (result.trades().isEmpty()) {
                throw new AssertionError("Expected at least one trade");
            }
        }
    }

    private static void assertClose(double actual, double expected, String field) {
        if (Math.abs(actual - expected) > 0.0001) {
            throw new AssertionError(field + " expected " + expected + " but got " + actual);
        }
    }
}
