package com.market.backend.domain;

import com.market.backend.metrics.RiskMetrics;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record BacktestResult(
        long eventsProcessed,
        Duration runtime,
        long usedMemoryBytes,
        BenchmarkStats benchmarkStats,
        double finalEquity,
        RiskMetrics metrics,
        List<Trade> trades,
        Map<String, Double> positions) {
}
