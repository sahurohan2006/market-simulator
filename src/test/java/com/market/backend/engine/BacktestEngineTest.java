package com.market.backend.engine;

import com.market.backend.domain.BacktestResult;
import com.market.backend.domain.ExecutionConfig;
import com.market.backend.metrics.MetricsService;
import com.market.backend.strategy.MovingAverageCrossoverStrategy;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestEngineTest {
    @Test
    void replaysSyntheticEventsAndReportsThroughput() {
        SyntheticMarketEventGenerator generator = new SyntheticMarketEventGenerator(
                10_000,
                4,
                Instant.parse("2024-01-01T09:30:00Z"),
                7L);

        BacktestResult result = new BacktestEngine(new MetricsService()).run(
                generator,
                new MovingAverageCrossoverStrategy(2, 3, 1.0),
                100_000.0,
                new ExecutionConfig(0.0, 0.0, false, 100.0));

        assertEquals(10_000, result.eventsProcessed());
        assertTrue(result.benchmarkStats().eventsPerSecond() > 0.0);
        assertTrue(result.benchmarkStats().p99LatencyNanos() >= result.benchmarkStats().p50LatencyNanos());
    }
}
