package com.market.backend.metrics;

import com.market.backend.domain.EquityPoint;
import com.market.backend.domain.Side;
import com.market.backend.domain.Trade;

import java.util.ArrayList;
import java.util.List;

public final class MetricsService {
    public RiskMetrics calculate(List<EquityPoint> equityCurve, double initialEquity) {
        return calculate(equityCurve, initialEquity, List.of());
    }

    public RiskMetrics calculate(List<EquityPoint> equityCurve, double initialEquity, List<Trade> trades) {
        if (equityCurve.isEmpty()) {
            return new RiskMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0);
        }
        double finalEquity = equityCurve.get(equityCurve.size() - 1).equity();
        double pnl = finalEquity - initialEquity;
        Drawdown drawdown = maxDrawdown(equityCurve);
        List<Double> returns = returns(equityCurve);
        double volatility = annualizedVolatility(returns);
        double sharpe = sharpeRatio(returns, volatility);
        double sortino = sortinoRatio(returns);
        double calmar = drawdown.percent() == 0.0 ? 0.0 : (pnl / initialEquity) / drawdown.percent();
        TradeStats tradeStats = tradeStats(trades);
        return new RiskMetrics(
                pnl,
                sharpe,
                volatility,
                sortino,
                calmar,
                drawdown.absolute(),
                drawdown.percent(),
                tradeStats.winRate(),
                tradeStats.averagePnl(),
                tradeStats.turnover(),
                equityCurve.size());
    }

    private List<Double> returns(List<EquityPoint> equityCurve) {
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < equityCurve.size(); i++) {
            double previous = equityCurve.get(i - 1).equity();
            double current = equityCurve.get(i).equity();
            if (previous != 0.0) {
                returns.add((current - previous) / previous);
            }
        }
        return returns;
    }

    private double sharpeRatio(List<Double> returns, double annualizedVolatility) {
        if (returns.size() < 2) {
            return 0.0;
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (annualizedVolatility == 0.0) {
            return 0.0;
        }
        return (mean * 252.0) / annualizedVolatility;
    }

    private double annualizedVolatility(List<Double> returns) {
        if (returns.size() < 2) {
            return 0.0;
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = 0.0;
        for (double value : returns) {
            variance += Math.pow(value - mean, 2);
        }
        variance /= returns.size() - 1;
        return Math.sqrt(variance) * Math.sqrt(252.0);
    }

    private double sortinoRatio(List<Double> returns) {
        if (returns.size() < 2) {
            return 0.0;
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double downside = 0.0;
        int downsideCount = 0;
        for (double value : returns) {
            if (value < 0.0) {
                downside += value * value;
                downsideCount++;
            }
        }
        if (downsideCount == 0) {
            return 0.0;
        }
        double downsideDeviation = Math.sqrt(downside / downsideCount) * Math.sqrt(252.0);
        return downsideDeviation == 0.0 ? 0.0 : (mean * 252.0) / downsideDeviation;
    }

    private Drawdown maxDrawdown(List<EquityPoint> equityCurve) {
        double peak = equityCurve.get(0).equity();
        double maxDrawdown = 0.0;
        double maxDrawdownPercent = 0.0;
        for (EquityPoint point : equityCurve) {
            peak = Math.max(peak, point.equity());
            double drawdown = peak - point.equity();
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
                maxDrawdownPercent = peak == 0.0 ? 0.0 : drawdown / peak;
            }
        }
        return new Drawdown(maxDrawdown, maxDrawdownPercent);
    }

    private record Drawdown(double absolute, double percent) {
    }

    private TradeStats tradeStats(List<Trade> trades) {
        if (trades.isEmpty()) {
            return new TradeStats(0.0, 0.0, 0.0);
        }
        int roundTrips = 0;
        int wins = 0;
        double realizedPnl = 0.0;
        double turnover = 0.0;
        double openQuantity = 0.0;
        double averageCost = 0.0;

        for (Trade trade : trades) {
            turnover += trade.grossNotional();
            if (trade.side() == Side.BUY) {
                double newQuantity = openQuantity + trade.quantity();
                averageCost = newQuantity == 0.0
                        ? 0.0
                        : ((averageCost * openQuantity) + trade.grossNotional() + trade.commission()) / newQuantity;
                openQuantity = newQuantity;
            } else {
                double closedQuantity = Math.min(openQuantity, trade.quantity());
                if (closedQuantity > 0.0) {
                    double tradePnl = (trade.price() * closedQuantity) - (averageCost * closedQuantity) - trade.commission();
                    realizedPnl += tradePnl;
                    roundTrips++;
                    if (tradePnl > 0.0) {
                        wins++;
                    }
                    openQuantity -= closedQuantity;
                    if (openQuantity == 0.0) {
                        averageCost = 0.0;
                    }
                }
            }
        }

        double winRate = roundTrips == 0 ? 0.0 : (double) wins / roundTrips;
        double averagePnl = roundTrips == 0 ? 0.0 : realizedPnl / roundTrips;
        return new TradeStats(winRate, averagePnl, turnover);
    }

    private record TradeStats(double winRate, double averagePnl, double turnover) {
    }
}
