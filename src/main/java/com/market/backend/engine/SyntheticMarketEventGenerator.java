package com.market.backend.engine;

import com.market.backend.domain.MarketEvent;

import java.time.Instant;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

public final class SyntheticMarketEventGenerator implements Iterable<MarketEvent> {
    private final long events;
    private final int symbols;
    private final Instant start;
    private final long seed;

    public SyntheticMarketEventGenerator(long events, int symbols, Instant start, long seed) {
        if (events < 0L) {
            throw new IllegalArgumentException("events cannot be negative");
        }
        if (symbols <= 0) {
            throw new IllegalArgumentException("symbols must be positive");
        }
        this.events = events;
        this.symbols = symbols;
        this.start = start;
        this.seed = seed;
    }

    @Override
    public Iterator<MarketEvent> iterator() {
        return new Iterator<>() {
            private final Random random = new Random(seed);
            private final double[] prices = initialPrices();
            private long index;

            @Override
            public boolean hasNext() {
                return index < events;
            }

            @Override
            public MarketEvent next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                int symbolIndex = (int) (index % symbols);
                double shock = random.nextGaussian() * 0.03;
                prices[symbolIndex] = Math.max(1.0, prices[symbolIndex] + shock);
                MarketEvent event = MarketEvent.price(
                        "SYM" + symbolIndex,
                        start.plusNanos(index * 1_000L),
                        prices[symbolIndex],
                        100.0 + random.nextInt(1_000));
                index++;
                return event;
            }

            private double[] initialPrices() {
                double[] result = new double[symbols];
                for (int i = 0; i < result.length; i++) {
                    result[i] = 100.0 + i;
                }
                return result;
            }
        };
    }
}
