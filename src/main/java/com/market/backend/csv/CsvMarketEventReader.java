package com.market.backend.csv;

import com.market.backend.domain.EventType;
import com.market.backend.domain.MarketEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;

public final class CsvMarketEventReader implements Iterable<MarketEvent>, AutoCloseable {
    private final BufferedReader reader;
    private final Map<String, Integer> columns;

    public CsvMarketEventReader(Path path) throws IOException {
        this.reader = open(path);
        String header = reader.readLine();
        if (header == null) {
            throw new IllegalArgumentException("CSV is empty: " + path);
        }
        this.columns = parseHeader(header);
    }

    @Override
    public Iterator<MarketEvent> iterator() {
        return new Iterator<>() {
            private String nextLine = readNext();

            @Override
            public boolean hasNext() {
                return nextLine != null;
            }

            @Override
            public MarketEvent next() {
                if (nextLine == null) {
                    throw new NoSuchElementException();
                }
                String current = nextLine;
                nextLine = readNext();
                return parse(current);
            }
        };
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    private String readNext() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    return line;
                }
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading CSV", e);
        }
    }

    private MarketEvent parse(String line) {
        String[] values = splitCsvLine(line);
        String symbol = get(values, "symbol", true);
        Instant timestamp = parseTimestamp(get(values, "timestamp", true));
        EventType type = EventType.valueOf(get(values, "type", false, "PRICE").trim().toUpperCase());
        if (type == EventType.ORDER_BOOK) {
            return MarketEvent.orderBook(
                    symbol,
                    timestamp,
                    parseDouble(get(values, "bid_price", true)),
                    parseDouble(get(values, "bid_size", false, "0")),
                    parseDouble(get(values, "ask_price", true)),
                    parseDouble(get(values, "ask_size", false, "0")));
        }
        return MarketEvent.price(
                symbol,
                timestamp,
                parseDouble(get(values, "price", true)),
                parseDouble(get(values, "volume", false, "0")));
    }

    private static Map<String, Integer> parseHeader(String header) {
        String[] names = splitCsvLine(header);
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            result.put(names[i].trim().toLowerCase(), i);
        }
        return result;
    }

    private String get(String[] values, String column, boolean required) {
        return get(values, column, required, "");
    }

    private String get(String[] values, String column, boolean required, String defaultValue) {
        Integer index = columns.get(column);
        String value = index == null || index >= values.length ? "" : values[index].trim();
        if (value.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException("Missing required CSV column/value: " + column);
            }
            return defaultValue;
        }
        return value;
    }

    private static double parseDouble(String value) {
        return value == null || value.isBlank() ? 0.0 : Double.parseDouble(value);
    }

    private static Instant parseTimestamp(String value) {
        String trimmed = value.trim();
        if (trimmed.endsWith("Z") || trimmed.contains("+")) {
            return Instant.parse(trimmed);
        }
        return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
    }

    private static String[] splitCsvLine(String line) {
        return line.split(",", -1);
    }

    private static BufferedReader open(Path path) throws IOException {
        InputStream input = Files.newInputStream(path);
        if (path.getFileName().toString().endsWith(".gz")) {
            input = new GZIPInputStream(input);
        }
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }
}
