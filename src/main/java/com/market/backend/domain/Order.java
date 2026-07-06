package com.market.backend.domain;

public record Order(String symbol, Side side, double quantity) {
    public Order {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        if (quantity <= 0.0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
