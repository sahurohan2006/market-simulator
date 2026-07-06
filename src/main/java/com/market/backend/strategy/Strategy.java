package com.market.backend.strategy;

import com.market.backend.domain.MarketEvent;
import com.market.backend.domain.Order;
import com.market.backend.domain.Portfolio;

import java.util.List;

public interface Strategy {
    String name();

    List<Order> onEvent(MarketEvent event, Portfolio portfolio);
}
