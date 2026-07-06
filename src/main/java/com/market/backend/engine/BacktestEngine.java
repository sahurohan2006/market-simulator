package com.market.backend.engine;

import com.market.backend.domain.BacktestResult;
import com.market.backend.domain.BenchmarkStats;
import com.market.backend.domain.EquityPoint;
import com.market.backend.domain.ExecutionConfig;
import com.market.backend.domain.MarketEvent;
import com.market.backend.domain.Order;
import com.market.backend.domain.Portfolio;
import com.market.backend.metrics.MetricsService;
import com.market.backend.strategy.Strategy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BacktestEngine {
    private final MetricsService metricsService;

    public BacktestEngine(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public BacktestResult run(Iterable<MarketEvent> events, Strategy strategy, double initialCash) {
        return run(events, strategy, initialCash, ExecutionConfig.conservativeDefaults());
    }

    public BacktestResult run(
            Iterable<MarketEvent> events,
            Strategy strategy,
            double initialCash,
            ExecutionConfig executionConfig) {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long startMemory = usedMemory(runtime);
        long startedNanos = System.nanoTime();

        Portfolio portfolio = new Portfolio(initialCash, executionConfig);
        Map<String, Double> lastPrices = new HashMap<>();
        List<EquityPoint> equityCurve = new ArrayList<>();
        LatencyRecorder latencyRecorder = new LatencyRecorder();
        long processed = 0;

        for (MarketEvent event : events) {
            long eventStarted = System.nanoTime();
            if (event.price() > 0.0) {
                lastPrices.put(event.symbol(), event.price());
            }
            List<Order> orders = strategy.onEvent(event, portfolio);
            for (Order order : orders) {
                portfolio.execute(event, order);
            }
            equityCurve.add(new EquityPoint(event.timestamp(), portfolio.markToMarket(lastPrices)));
            processed++;
            latencyRecorder.record(System.nanoTime() - eventStarted);
        }

        long elapsedNanos = System.nanoTime() - startedNanos;
        Duration runtimeDuration = Duration.ofNanos(elapsedNanos);
        long memoryDelta = Math.max(0L, usedMemory(runtime) - startMemory);
        double finalEquity = equityCurve.isEmpty() ? initialCash : equityCurve.get(equityCurve.size() - 1).equity();
        BenchmarkStats benchmarkStats = latencyRecorder.stats(processed, elapsedNanos);
        return new BacktestResult(
                processed,
                runtimeDuration,
                memoryDelta,
                benchmarkStats,
                finalEquity,
                metricsService.calculate(equityCurve, initialCash, portfolio.trades()),
                List.copyOf(portfolio.trades()),
                Map.copyOf(portfolio.positions()));
    }

    private long usedMemory(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static final class LatencyRecorder {
        private static final int MAX_SAMPLES = 100_000;
        private final long[] samples = new long[MAX_SAMPLES];
        private int size;
        private long totalNanos;

        private void record(long nanos) {
            totalNanos += nanos;
            if (size < MAX_SAMPLES) {
                samples[size++] = nanos;
            }
        }

        private BenchmarkStats stats(long events, long elapsedNanos) {
            if (events == 0L || elapsedNanos == 0L) {
                return new BenchmarkStats(0.0, 0.0, 0L, 0L, 0L);
            }
            long[] copy = java.util.Arrays.copyOf(samples, size);
            java.util.Arrays.sort(copy);
            return new BenchmarkStats(
                    events / (elapsedNanos / 1_000_000_000.0),
                    (double) totalNanos / events,
                    percentile(copy, 0.50),
                    percentile(copy, 0.95),
                    percentile(copy, 0.99));
        }

        private long percentile(long[] sortedSamples, double percentile) {
            if (sortedSamples.length == 0) {
                return 0L;
            }
            int index = (int) Math.ceil(percentile * sortedSamples.length) - 1;
            return sortedSamples[Math.max(0, Math.min(index, sortedSamples.length - 1))];
        }
    }
}
