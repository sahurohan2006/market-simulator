package com.market.backend.book;

import com.market.backend.domain.Side;

/**
 * A single match between an incoming (taker) order and a resting (maker)
 * order already in the book. Price is always the resting maker's price,
 * consistent with standard price-time priority matching semantics.
 */
public record Fill(
        long sequence,
        String symbol,
        long makerOrderId,
        long takerOrderId,
        Side takerSide,
        double price,
        double quantity) {
}
