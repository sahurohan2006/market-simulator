package com.market.backend.domain;

public record BenchmarkStats(
        double eventsPerSecond,
        double averageLatencyNanos,
        long p50LatencyNanos,
        long p95LatencyNanos,
        long p99LatencyNanos) {
}
