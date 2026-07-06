package com.market.backend.db;

import com.market.backend.domain.EventType;
import com.market.backend.domain.MarketEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class MarketEventRepository {
    private static final int BATCH_SIZE = 5_000;

    public long insert(Connection connection, Iterable<MarketEvent> events, String sourceFile) throws SQLException {
        String sql = """
                INSERT INTO market_events
                    (symbol, event_time, event_type, price, volume, bid_price, bid_size, ask_price, ask_size, source_file)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        long count = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (MarketEvent event : events) {
                statement.setString(1, event.symbol());
                statement.setTimestamp(2, Timestamp.from(event.timestamp()));
                statement.setString(3, event.type().name());
                statement.setDouble(4, event.price());
                statement.setDouble(5, event.volume());
                statement.setDouble(6, event.bidPrice());
                statement.setDouble(7, event.bidSize());
                statement.setDouble(8, event.askPrice());
                statement.setDouble(9, event.askSize());
                statement.setString(10, sourceFile);
                statement.addBatch();
                count++;
                if (count % BATCH_SIZE == 0) {
                    statement.executeBatch();
                    connection.commit();
                }
            }
            statement.executeBatch();
            connection.commit();
            return count;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public Iterable<MarketEvent> replay(Connection connection, String symbol) {
        return () -> new DatabaseMarketEventIterator(connection, symbol);
    }

    private static final class DatabaseMarketEventIterator implements Iterator<MarketEvent> {
        private final PreparedStatement statement;
        private final ResultSet resultSet;
        private boolean hasNext;

        private DatabaseMarketEventIterator(Connection connection, String symbol) {
            try {
                String sql = """
                        SELECT symbol, event_time, event_type, price, volume, bid_price, bid_size, ask_price, ask_size
                        FROM market_events
                        WHERE (? IS NULL OR symbol = ?)
                        ORDER BY event_time, id
                        """;
                this.statement = connection.prepareStatement(
                        sql,
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY);
                statement.setFetchSize(10_000);
                statement.setString(1, symbol);
                statement.setString(2, symbol);
                this.resultSet = statement.executeQuery();
                this.hasNext = resultSet.next();
            } catch (SQLException e) {
                throw new IllegalStateException("Failed opening replay cursor", e);
            }
        }

        @Override
        public boolean hasNext() {
            if (!hasNext) {
                closeQuietly();
            }
            return hasNext;
        }

        @Override
        public MarketEvent next() {
            if (!hasNext) {
                throw new NoSuchElementException();
            }
            try {
                MarketEvent event = map(resultSet);
                hasNext = resultSet.next();
                if (!hasNext) {
                    closeQuietly();
                }
                return event;
            } catch (SQLException e) {
                closeQuietly();
                throw new IllegalStateException("Failed reading replay cursor", e);
            }
        }

        private MarketEvent map(ResultSet rs) throws SQLException {
            String symbol = rs.getString("symbol");
            Instant timestamp = rs.getTimestamp("event_time").toInstant();
            EventType type = EventType.valueOf(rs.getString("event_type"));
            if (type == EventType.ORDER_BOOK) {
                return MarketEvent.orderBook(
                        symbol,
                        timestamp,
                        rs.getDouble("bid_price"),
                        rs.getDouble("bid_size"),
                        rs.getDouble("ask_price"),
                        rs.getDouble("ask_size"));
            }
            return MarketEvent.price(symbol, timestamp, rs.getDouble("price"), rs.getDouble("volume"));
        }

        private void closeQuietly() {
            try {
                resultSet.close();
            } catch (SQLException ignored) {
            }
            try {
                statement.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
