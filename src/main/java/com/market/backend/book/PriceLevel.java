package com.market.backend.book;

/**
 * Aggregated snapshot of resting quantity at a single price level,
 * used for depth-of-book reporting.
 */
public record PriceLevel(double price, double totalQuantity, int orderCount) {
}
