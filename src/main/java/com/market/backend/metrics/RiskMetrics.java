package com.market.backend.metrics;

public record RiskMetrics(
        double pnl,
        double sharpeRatio,
        double annualizedVolatility,
        double sortinoRatio,
        double calmarRatio,
        double maxDrawdown,
        double maxDrawdownPercent,
        double winRate,
        double averageTradePnl,
        double turnover,
        long observations) {
}
