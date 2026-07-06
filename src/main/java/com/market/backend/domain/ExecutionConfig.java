package com.market.backend.domain;

public record ExecutionConfig(
        double commissionPerShare,
        double slippageBps,
        boolean allowShorting,
        double maxAbsolutePosition) {
    public static ExecutionConfig conservativeDefaults() {
        return new ExecutionConfig(0.005, 1.0, false, 1_000.0);
    }

    public ExecutionConfig {
        if (commissionPerShare < 0.0) {
            throw new IllegalArgumentException("commissionPerShare cannot be negative");
        }
        if (slippageBps < 0.0) {
            throw new IllegalArgumentException("slippageBps cannot be negative");
        }
        if (maxAbsolutePosition <= 0.0) {
            throw new IllegalArgumentException("maxAbsolutePosition must be positive");
        }
    }
}
