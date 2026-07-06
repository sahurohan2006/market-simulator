package com.market.backend.domain;

import java.time.Instant;

public record Trade(
        Instant timestamp,
        String symbol,
        Side side,
        double quantity,
        double price,
        double grossNotional,
        double commission,
        double slippage,
        double cashAfter,
        double positionAfter) {
}
