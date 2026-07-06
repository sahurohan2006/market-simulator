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

Treat this as a baseline, not a universal result. Hardware, JVM warmup, GC, strategy complexity, and whether data comes from CSV/PostgreSQL will change throughput.

## Execution Model

Execution assumptions are explicit and configurable through environment variables:

```bash
COMMISSION_PER_SHARE=0.005
SLIPPAGE_BPS=1.0
ALLOW_SHORTING=false
MAX_ABSOLUTE_POSITION=1000
```

Price events fill at the event price plus/minus slippage. Order-book events fill buys at ask and sells at bid, then apply slippage and commission. Orders that violate shorting or inventory constraints are rejected.

## Resume Framing

Possible resume bullet:

> Built a Java/PostgreSQL market replay and backtesting engine for historical trade/quote data with compressed CSV ingestion, JDBC batch persistence, 1M+ event synthetic benchmarks, pluggable strategies, transaction-cost-aware execution, PnL/risk analytics, and throughput/latency/memory reporting.
