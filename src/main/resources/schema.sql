CREATE TABLE IF NOT EXISTS market_events (
    id BIGSERIAL PRIMARY KEY,
    symbol TEXT NOT NULL,
    event_time TIMESTAMPTZ NOT NULL,
    event_type TEXT NOT NULL CHECK (event_type IN ('PRICE', 'ORDER_BOOK')),
    price NUMERIC(20, 8),
    volume NUMERIC(20, 8),
    bid_price NUMERIC(20, 8),
    bid_size NUMERIC(20, 8),
    ask_price NUMERIC(20, 8),
    ask_size NUMERIC(20, 8),
    source_file TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_market_events_replay
    ON market_events (symbol, event_time, id);

CREATE INDEX IF NOT EXISTS idx_market_events_type
    ON market_events (event_type);
