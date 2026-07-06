package com.market.backend.domain;

import java.time.Instant;
import java.util.Objects;

public final class MarketEvent implements Comparable<MarketEvent> {
    private final String symbol;
    private final Instant timestamp;
    private final EventType type;
    private final double price;
    private final double volume;
    private final double bidPrice;
    private final double bidSize;
    private final double askPrice;
    private final double askSize;

    private MarketEvent(
            String symbol,
            Instant timestamp,
            EventType type,
            double price,
            double volume,
            double bidPrice,
            double bidSize,
            double askPrice,
            double askSize) {
        this.symbol = Objects.requireNonNull(symbol, "symbol").trim().toUpperCase();
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.type = Objects.requireNonNull(type, "type");
        this.price = price;
        this.volume = volume;
        this.bidPrice = bidPrice;
        this.bidSize = bidSize;
        this.askPrice = askPrice;
        this.askSize = askSize;
    }

    public static MarketEvent price(String symbol, Instant timestamp, double price, double volume) {
        return new MarketEvent(symbol, timestamp, EventType.PRICE, price, volume, 0.0, 0.0, 0.0, 0.0);
    }

    public static MarketEvent orderBook(
            String symbol,
            Instant timestamp,
            double bidPrice,
            double bidSize,
            double askPrice,
            double askSize) {
        double mid = bidPrice > 0.0 && askPrice > 0.0 ? (bidPrice + askPrice) / 2.0 : 0.0;
        return new MarketEvent(symbol, timestamp, EventType.ORDER_BOOK, mid, 0.0, bidPrice, bidSize, askPrice, askSize);
    }

    public String symbol() {
        return symbol;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public EventType type() {
        return type;
    }

    public double price() {
        return price;
    }

    public double volume() {
        return volume;
    }

    public double bidPrice() {
        return bidPrice;
    }

    public double bidSize() {
        return bidSize;
    }

    public double askPrice() {
        return askPrice;
    }

    public double askSize() {
        return askSize;
    }

    @Override
    public int compareTo(MarketEvent other) {
        int byTime = timestamp.compareTo(other.timestamp);
        if (byTime != 0) {
            return byTime;
        }
        return symbol.compareTo(other.symbol);
    }
}
