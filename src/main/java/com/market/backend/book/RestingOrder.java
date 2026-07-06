package com.market.backend.book;

import com.market.backend.domain.Side;

/**
 * A resting limit order sitting in the book at a specific price level.
 * Mutable remaining quantity so partial fills can be applied in place
 * without losing the order's time priority within its price level.
 */
public final class RestingOrder {
    private final long orderId;
    private final Side side;
    private final double price;
    private final long sequence;
    private double remainingQuantity;

    RestingOrder(long orderId, Side side, double price, double quantity, long sequence) {
        this.orderId = orderId;
        this.side = side;
        this.price = price;
        this.remainingQuantity = quantity;
        this.sequence = sequence;
    }

    public long orderId() {
        return orderId;
    }

    public Side side() {
        return side;
    }

    public double price() {
        return price;
    }

    public double remainingQuantity() {
        return remainingQuantity;
    }

    public long sequence() {
        return sequence;
    }

    void reduce(double quantity) {
        remainingQuantity -= quantity;
    }

    boolean isFilled() {
        return remainingQuantity <= 1e-9;
    }
}
