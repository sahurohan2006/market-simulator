package com.market.backend.strategy;

import com.market.backend.domain.MarketEvent;
import com.market.backend.domain.Order;
import com.market.backend.domain.Portfolio;
import com.market.backend.domain.Side;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public final class MovingAverageCrossoverStrategy implements Strategy {
    private final int shortWindow;
    private final int longWindow;
    private final double tradeQuantity;
    private final Queue<Double> shortPrices = new ArrayDeque<>();
    private final Queue<Double> longPrices = new ArrayDeque<>();
    private double shortSum;
    private double longSum;
    private int signal;

    public MovingAverageCrossoverStrategy(int shortWindow, int longWindow, double tradeQuantity) {
        if (shortWindow <= 0 || longWindow <= shortWindow) {
            throw new IllegalArgumentException("Expected 0 < shortWindow < longWindow");
        }
        this.shortWindow = shortWindow;
        this.longWindow = longWindow;
        this.tradeQuantity = tradeQuantity;
    }

    @Override
    public String name() {
        return "moving-average-crossover";
    }

    @Override
    public List<Order> onEvent(MarketEvent event, Portfolio portfolio) {
        if (event.price() <= 0.0) {
            return List.of();
        }
        shortSum = add(shortPrices, shortSum, event.price(), shortWindow);
        longSum = add(longPrices, longSum, event.price(), longWindow);
        if (longPrices.size() < longWindow) {
            return List.of();
        }

        double shortAverage = shortSum / shortWindow;
        double longAverage = longSum / longWindow;
        int nextSignal = Double.compare(shortAverage, longAverage);
        if (nextSignal > 0 && signal <= 0) {
            signal = nextSignal;
            return List.of(new Order(event.symbol(), Side.BUY, tradeQuantity));
        }
        if (nextSignal < 0 && signal >= 0 && portfolio.position(event.symbol()) > 0.0) {
            signal = nextSignal;
            return List.of(new Order(event.symbol(), Side.SELL, Math.min(tradeQuantity, portfolio.position(event.symbol()))));
        }
        signal = nextSignal;
        return List.of();
    }

    private double add(Queue<Double> prices, double sum, double price, int maxSize) {
        prices.add(price);
        sum += price;
        if (prices.size() > maxSize) {
            sum -= prices.remove();
        }
        return sum;
    }
}
