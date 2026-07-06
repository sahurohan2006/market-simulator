package com.market.backend.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Portfolio {
    private double cash;
    private final ExecutionConfig executionConfig;
    private final Map<String, Double> positions = new HashMap<>();
    private final List<Trade> trades = new ArrayList<>();

    public Portfolio(double initialCash) {
        this(initialCash, ExecutionConfig.conservativeDefaults());
    }

    public Portfolio(double initialCash, ExecutionConfig executionConfig) {
        this.cash = initialCash;
        this.executionConfig = executionConfig;
    }

    public void execute(MarketEvent event, Order order) {
        double basePrice = fillPrice(event, order.side());
        double slippage = basePrice * (executionConfig.slippageBps() / 10_000.0);
        double fillPrice = order.side() == Side.BUY ? basePrice + slippage : basePrice - slippage;
        double signedQuantity = order.side() == Side.BUY ? order.quantity() : -order.quantity();
        double currentPosition = positions.getOrDefault(order.symbol(), 0.0);
        double newPosition = currentPosition + signedQuantity;
        if (!executionConfig.allowShorting() && newPosition < -0.0000001) {
            return;
        }
        if (Math.abs(newPosition) > executionConfig.maxAbsolutePosition()) {
            return;
        }

        double grossNotional = order.quantity() * fillPrice;
        double commission = order.quantity() * executionConfig.commissionPerShare();
        cash -= signedQuantity * fillPrice;
        cash -= commission;
        positions.put(order.symbol(), newPosition);
        trades.add(new Trade(
                event.timestamp(),
                order.symbol(),
                order.side(),
                order.quantity(),
                fillPrice,
                grossNotional,
                commission,
                slippage * order.quantity(),
                cash,
                newPosition));
    }

    public double markToMarket(Map<String, Double> lastPrices) {
        double equity = cash;
        for (Map.Entry<String, Double> entry : positions.entrySet()) {
            equity += entry.getValue() * lastPrices.getOrDefault(entry.getKey(), 0.0);
        }
        return equity;
    }

    public double position(String symbol) {
        return positions.getOrDefault(symbol, 0.0);
    }

    public double cash() {
        return cash;
    }

    public List<Trade> trades() {
        return Collections.unmodifiableList(trades);
    }

    public Map<String, Double> positions() {
        return Collections.unmodifiableMap(positions);
    }

    private double fillPrice(MarketEvent event, Side side) {
        if (event.type() == EventType.ORDER_BOOK) {
            if (side == Side.BUY && event.askPrice() > 0.0) {
                return event.askPrice();
            }
            if (side == Side.SELL && event.bidPrice() > 0.0) {
                return event.bidPrice();
            }
        }
        return event.price();
    }
}
