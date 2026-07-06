# Market Simulator Backend

Java 17 backend for ingesting historical market CSV data, normalizing it into PostgreSQL, replaying events chronologically, running simple strategies, and reporting PnL/risk/runtime metrics.

## Features

- Streams CSV market data with columns:
  `symbol,timestamp,type,price,volume,bid_price,bid_size,ask_price,ask_size`
- Supports plain `.csv` and compressed `.csv.gz` inputs.
- Stores normalized `PRICE` and `ORDER_BOOK` events in PostgreSQL.
- Replays large datasets chronologically from PostgreSQL with a forward-only JDBC cursor.
- Includes moving average crossover and simple market making strategies.
- Uses configurable commission, slippage, shorting, and inventory limits.
- Tracks trade history, positions, final equity, PnL, Sharpe, Sortino, Calmar, volatility, win rate, turnover, max drawdown, runtime, memory delta, throughput, and latency percentiles.
- Includes a standalone price-time-priority limit order book matching engine with partial fills, multi-level sweeps, and order cancellation.

## Run Locally

```bash
cd market-simulator
mvn compile
mvn exec:java -Dexec.args="replay-csv data/sample-prices.csv ma"
mvn exec:java -Dexec.args="benchmark data/sample-prices.csv ma"
mvn exec:java -Dexec.args="benchmark-synthetic 1000000 ma 10"
```

Run tests:

```bash
cd market-simulator
mvn test
```

## PostgreSQL

Start PostgreSQL:

```bash
cd market-simulator
docker compose up -d
```

Ingest CSV:

```bash
DB_URL=jdbc:postgresql://localhost:5432/market \
DB_USER=market \
DB_PASSWORD=market \
mvn exec:java -Dexec.args="ingest data/sample-prices.csv"
```

Replay from PostgreSQL:

```bash
DB_URL=jdbc:postgresql://localhost:5432/market \
DB_USER=market \
DB_PASSWORD=market \
mvn exec:java -Dexec.args="replay-db AAPL ma"
```

## Real Historical Dataset

`data/market_history_2019_2024.csv.gz` contains 18,108 events derived from real
daily OHLCV data (Yahoo Finance, via `yfinance`) for AAPL, MSFT, and SPY from
2019-01-01 through 2024-12-30 (six years). Each trading day's open/high/low/close
is expanded into four synthetic intraday `PRICE` timestamps (09:30/11:00/13:30/16:00)
to give the replay engine a chronological event stream; this is a derived/synthetic
intraday schedule, not real tick-level intraday data.

Ingest and replay it end-to-end:

```bash
docker compose up -d
DB_URL=jdbc:postgresql://localhost:5432/market DB_USER=market DB_PASSWORD=market \
  mvn exec:java -Dexec.args="ingest data/market_history_2019_2024.csv.gz"
DB_URL=jdbc:postgresql://localhost:5432/market DB_USER=market DB_PASSWORD=market \
  mvn exec:java -Dexec.args="replay-db ALL ma"
```

## Scaling Notes

For 1M+ events, ingest uses 5,000-row JDBC batches and replay uses `ORDER BY event_time, id` with an index on `(symbol, event_time, id)`. Keep source CSV sorted when using `replay-csv`; use `replay-db` when multiple files or symbols need guaranteed chronological ordering.

Recent local benchmark on Apple Silicon/Homebrew JDK 26, using Java 17 bytecode:

```text
mvn exec:java -Dexec.args="benchmark-synthetic 1000000 ma 10"

Events processed: 1,000,000
Throughput: 4,454,260 events/sec
Runtime: 0.225 seconds
Memory delta: 239.68 MB
Trades: 2,110
```

Treat this as a baseline, not a universal result. Hardware, JVM warmup, GC, strategy complexity, and whether data comes from CSV/PostgreSQL will change throughput. **This synthetic-generator number reflects only the in-memory strategy/portfolio loop — it does not include PostgreSQL I/O.**

For comparison, here is a real `replay-db` run against the 6-year, 18,108-event
historical dataset above, which does include the JDBC cursor query and network
round trip to PostgreSQL:

```text
mvn exec:java -Dexec.args="replay-db ALL ma"

Events processed: 18,108
Throughput: 280,136 events/sec
Runtime: 0.065 seconds
Latency nanos avg/p50/p95/p99: 484 / 292 / 1208 / 2583
Memory delta: 10.93 MB
Final equity: $426,746.66
PnL: $326,746.66
Sharpe: 0.2028
Max drawdown: 41.40%
Trades: 100
```

DB-backed replay is roughly an order of magnitude slower than the pure in-memory
synthetic benchmark, which is expected once JDBC network round trips and query
execution are in the critical path.

## Execution Model

Execution assumptions are explicit and configurable through environment variables:

```bash
COMMISSION_PER_SHARE=0.005
SLIPPAGE_BPS=1.0
ALLOW_SHORTING=false
MAX_ABSOLUTE_POSITION=1000
```

Price events fill at the event price plus/minus slippage. Order-book events fill buys at ask and sells at bid, then apply slippage and commission. Orders that violate shorting or inventory constraints are rejected.

## Limit Order Book Matching Engine

`com.market.backend.book.OrderBook` is a standalone price-time-priority matching
engine, independent of the CSV/PostgreSQL replay pipeline above:

- Bids/asks are kept in separate price-ordered maps, each price level holding a
  FIFO queue so that, within a level, the earliest resting order always fills first.
- Limit orders match immediately against any crossing resting orders (potentially
  sweeping multiple price levels and partially filling individual resting orders),
  then rest any unfilled remainder in the book.
- Market orders sweep the book regardless of price; any unfilled remainder is
  dropped rather than resting, modeling an immediate-or-cancel order.
- Orders can be cancelled by id in O(1) average lookup plus O(level size) removal.

Run the synthetic order-flow demo:

```bash
mvn exec:java -Dexec.args="lob-demo 100000 7"
```

```text
Orders submitted: 100,000
Fills generated: 90,994
Volume filled: 429,299 shares
Throughput: 4,293,942 orders/sec
Best bid/ask: 120.27 / 120.30
Spread: 0.0300
```

Covered by `OrderBookTest`: resting non-crossing orders, exact and partial fills,
price-time priority within a level, multi-level market order sweeps, unfilled
market order remainder handling, and cancellation.

## Resume Framing

Possible resume bullets:

> Built a Java backtesting engine executing moving-average and market-making strategies over 1M+ synthetic and 6 years of real historical trade events, with slippage-, commission-, and position-limit-aware order execution.

> Implemented a price-time-priority limit order book matching engine in Java supporting partial fills, multi-level sweeps, and order cancellation, validated with unit tests covering priority, partial fills, and edge cases.

> Designed a PostgreSQL-backed event store with 5,000-row JDBC batch ingestion and a `(symbol, event_time, id)` index, enabling forward-only chronological replay validated against a real 6-year, multi-symbol historical dataset.

> Achieved ~4.5M events/sec in-memory backtest throughput (280K events/sec end-to-end with PostgreSQL-backed replay) and instrumented the engine with p50/p95/p99 latency, memory delta, and risk analytics (Sharpe, Sortino, Calmar, max drawdown, turnover); validated with JUnit and CI on every push.
