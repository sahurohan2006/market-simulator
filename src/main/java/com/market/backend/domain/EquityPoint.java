package com.market.backend.domain;

import java.time.Instant;

public record EquityPoint(Instant timestamp, double equity) {
}
