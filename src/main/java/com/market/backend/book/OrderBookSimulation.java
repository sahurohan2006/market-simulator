package com.market.backend.book;

import com.market.backend.domain.Side;

import java.util.List;
import java.util.Random;

/**
 * Generates synthetic limit/market order flow against an {@link OrderBook}
 * and reports matching statistics. Useful for exercising and benchmarking
 * the matching engine independent of the CSV/PostgreSQL replay pipeline.
 */
public final class OrderBookSimulation {
    private final OrderBook book;
    private final Random random;
    private double midPrice;
    private long nextOrderId = 1;

    private long ordersSubmitted;
    private long fillsGenerated;
    private double volumeFilled;

    public OrderBookSimulation(String symbol, double startingMidPrice, long seed) {
        this.book = new OrderBook(symbol);
        this.midPrice = startingMidPrice;
        this.random = new Random(seed);
    }

    /** Runs the simulation for the given number of synthetic orders and returns the result. */
    public Result run(int orderCount) {
        long startedNanos = System.nanoTime();
        for (int i = 0; i < orderCount; i++) {
            step();
        }
        long elapsedNanos = System.nanoTime() - startedNanos;
        return new Result(
                ordersSubmitted,
                fillsGenerated,
                volumeFilled,
                book.bestBid(),
                book.bestAsk(),
                book.spread(),
                book.bidLevels(5),
                book.askLevels(5),
                elapsedNanos);
    }

    private void step() {
        // Random walk the reference mid price so quotes drift realistically.
        midPrice = Math.max(1.0, midPrice + (random.nextDouble() - 0.5) * 0.10);
        long orderId = nextOrderId++;
        ordersSubmitted++;

        List<Fill> fills;
        // 70% resting limit orders (liquidity), 30% aggressive market orders (liquidity takers).
        if (random.nextDouble() < 0.7) {
            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            double offset = 0.01 + random.nextDouble() * 0.20;
            double price = side == Side.BUY ? round(midPrice - offset) : round(midPrice + offset);
            double quantity = 1 + random.nextInt(20);
            fills = book.submitLimitOrder(orderId, side, price, quantity);
        } else {
            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            double quantity = 1 + random.nextInt(10);
            fills = book.submitMarketOrder(orderId, side, quantity);
        }
        fillsGenerated += fills.size();
        for (Fill fill : fills) {
            volumeFilled += fill.quantity();
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record Result(
            long ordersSubmitted,
            long fillsGenerated,
            double volumeFilled,
            Double bestBid,
            Double bestAsk,
            Double spread,
            List<PriceLevel> bidDepth,
            List<PriceLevel> askDepth,
            long elapsedNanos) {

        public double ordersPerSecond() {
            return elapsedNanos == 0L ? 0.0 : ordersSubmitted / (elapsedNanos / 1_000_000_000.0);
        }
    }
}
