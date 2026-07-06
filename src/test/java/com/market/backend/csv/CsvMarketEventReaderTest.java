package com.market.backend.csv;

import com.market.backend.domain.EventType;
import com.market.backend.domain.MarketEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvMarketEventReaderTest {
    @Test
    void parsesPriceRows() throws Exception {
        try (CsvMarketEventReader reader = new CsvMarketEventReader(Path.of("data/sample-prices.csv"))) {
            Iterator<MarketEvent> iterator = reader.iterator();
            MarketEvent event = iterator.next();

            assertEquals("AAPL", event.symbol());
            assertEquals(EventType.PRICE, event.type());
            assertEquals(100.00, event.price(), 0.0001);
            assertEquals(1_000.0, event.volume(), 0.0001);
        }
    }

    @Test
    void parsesOrderBookRows() throws Exception {
        try (CsvMarketEventReader reader = new CsvMarketEventReader(Path.of("data/sample-order-book.csv"))) {
            MarketEvent event = reader.iterator().next();

            assertEquals(EventType.ORDER_BOOK, event.type());
            assertEquals(99.95, event.bidPrice(), 0.0001);
            assertEquals(100.05, event.askPrice(), 0.0001);
            assertEquals(100.00, event.price(), 0.0001);
        }
    }
}
