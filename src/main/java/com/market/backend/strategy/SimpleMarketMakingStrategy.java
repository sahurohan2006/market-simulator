package com.market.backend.strategy;

import com.market.backend.domain.EventType;
import com.market.backend.domain.MarketEvent;
import com.market.backend.domain.Order;
import com.market.backend.domain.Portfolio;
import com.market.backend.domain.Side;

import java.util.ArrayList;
import java.util.List;

public final class SimpleMarketMakingStrategy implements Strategy {
    private final double targetSpreadBps;
    private final double quoteQuantity;
    private final double maxInventory;

    public SimpleMarketMakingStrategy(double targetSpreadBps, double quoteQuantity, double maxInventory) {
        this.targetSpreadBps = targetSpreadBps;
        this.quoteQuantity = quoteQuantity;
        this.maxInventory = maxInventory;
    }

    @Override
    public String name() {
        return "simple-market-making";
    }

    @Override
    public List<Order> onEvent(MarketEvent event, Portfolio portfolio) {
        if (event.type() != EventType.ORDER_BOOK || event.bidPrice() <= 0.0 || event.askPrice() <= 0.0) {
            return List.of();
        }
        double mid = (event.bidPrice() + event.askPrice()) / 2.0;
        double spreadBps = ((event.askPrice() - event.bidPrice()) / mid) * 10_000.0;
        if (spreadBps < targetSpreadBps) {
            return List.of();
        }

        double inventory = portfolio.position(event.symbol());
        List<Order> orders = new ArrayList<>(2);
        if (inventory < maxInventory) {
            orders.add(new Order(event.symbol(), Side.BUY, Math.min(quoteQuantity, maxInventory - inventory)));
        }
        if (inventory > -maxInventory) {
            orders.add(new Order(event.symbol(), Side.SELL, Math.min(quoteQuantity, maxInventory + inventory)));
        }
        return orders;
    }
}
