package com.market.backend.app;

import com.market.backend.book.OrderBookSimulation;
import com.market.backend.book.PriceLevel;
import com.market.backend.csv.CsvIngestionService;
import com.market.backend.csv.CsvMarketEventReader;
import com.market.backend.db.Database;
import com.market.backend.db.MarketEventRepository;
import com.market.backend.domain.BacktestResult;
import com.market.backend.domain.ExecutionConfig;
import com.market.backend.domain.MarketEvent;
import com.market.backend.engine.BacktestEngine;
import com.market.backend.engine.SyntheticMarketEventGenerator;
import com.market.backend.metrics.MetricsService;
import com.market.backend.strategy.MovingAverageCrossoverStrategy;
import com.market.backend.strategy.SimpleMarketMakingStrategy;
import com.market.backend.strategy.Strategy;

import java.nio.file.Path;
import java.sql.Connection;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;

public final class MarketSimulatorApp {
    private static final double DEFAULT_INITIAL_CASH = 100_000.0;

    private MarketSimulatorApp() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            usage();
            return;
        }

        String command = args[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "ingest" -> ingest(args);
            case "replay-csv" -> replayCsv(args);
            case "replay-db" -> replayDb(args);
            case "benchmark" -> benchmark(args);
            case "benchmark-synthetic" -> benchmarkSynthetic(args);
            case "lob-demo" -> lobDemo(args);
            default -> {
                System.err.println("Unknown command: " + args[0]);
                usage();
                System.exit(2);
            }
        }
    }

    private static void ingest(String[] args) throws Exception {
        requireArgs(args, 2, "ingest <csv-path>");
        Database database = databaseFromEnv();
        long rows = new CsvIngestionService(database, new MarketEventRepository()).ingest(Path.of(args[1]));
        System.out.printf("Ingested %,d market events%n", rows);
    }

    private static void replayCsv(String[] args) throws Exception {
        requireArgs(args, 3, "replay-csv <csv-path> <ma|mm>");
        try (CsvMarketEventReader reader = new CsvMarketEventReader(Path.of(args[1]))) {
            BacktestResult result = engine().run(reader, strategy(args[2]), DEFAULT_INITIAL_CASH, executionConfigFromEnv());
            printResult(result);
        }
    }

    private static void replayDb(String[] args) throws Exception {
        requireArgs(args, 3, "replay-db <symbol|ALL> <ma|mm>");
        String symbol = "ALL".equalsIgnoreCase(args[1]) ? null : args[1].trim().toUpperCase();
        try (Connection connection = databaseFromEnv().connect()) {
            Iterable<MarketEvent> events = new MarketEventRepository().replay(connection, symbol);
            BacktestResult result = engine().run(events, strategy(args[2]), DEFAULT_INITIAL_CASH, executionConfigFromEnv());
            printResult(result);
        }
    }

    private static void benchmark(String[] args) throws Exception {
        requireArgs(args, 3, "benchmark <csv-path> <ma|mm>");
        long started = System.nanoTime();
        replayCsv(args);
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        System.out.printf("Wall-clock benchmark: %.3f seconds%n", seconds);
    }

    private static void benchmarkSynthetic(String[] args) {
        requireArgs(args, 3, "benchmark-synthetic <events> <ma|mm> [symbols]");
        long events = Long.parseLong(args[1]);
        int symbols = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
        SyntheticMarketEventGenerator generator = new SyntheticMarketEventGenerator(
                events,
                symbols,
                Instant.parse("2024-01-01T09:30:00Z"),
                42L);
        BacktestResult result = engine().run(generator, strategy(args[2]), DEFAULT_INITIAL_CASH, executionConfigFromEnv());
        printResult(result);
    }

    private static void lobDemo(String[] args) {
        requireArgs(args, 2, "lob-demo <orders> [seed]");
        int orders = Integer.parseInt(args[1]);
        long seed = args.length >= 3 ? Long.parseLong(args[2]) : 42L;
        OrderBookSimulation simulation = new OrderBookSimulation("SYNTH", 100.0, seed);
        OrderBookSimulation.Result result = simulation.run(orders);

        System.out.printf("Orders submitted: %,d%n", result.ordersSubmitted());
        System.out.printf("Fills generated: %,d%n", result.fillsGenerated());
        System.out.printf("Volume filled: %,.0f shares%n", result.volumeFilled());
        System.out.printf("Throughput: %,.0f orders/sec%n", result.ordersPerSecond());
        System.out.printf("Best bid/ask: %.2f / %.2f%n", result.bestBid(), result.bestAsk());
        System.out.printf("Spread: %.4f%n", result.spread());
        System.out.println("Top bid levels (price, qty, orders):");
        for (PriceLevel level : result.bidDepth()) {
            System.out.printf("  %.2f  qty=%.0f  orders=%d%n", level.price(), level.totalQuantity(), level.orderCount());
        }
        System.out.println("Top ask levels (price, qty, orders):");
        for (PriceLevel level : result.askDepth()) {
            System.out.printf("  %.2f  qty=%.0f  orders=%d%n", level.price(), level.totalQuantity(), level.orderCount());
        }
    }

    private static Strategy strategy(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "ma", "moving-average" -> new MovingAverageCrossoverStrategy(2, 3, 10.0);
            case "mm", "market-making" -> new SimpleMarketMakingStrategy(5.0, 1.0, 25.0);
            default -> throw new IllegalArgumentException("Unknown strategy: " + name);
        };
    }

    private static BacktestEngine engine() {
        return new BacktestEngine(new MetricsService());
    }

    private static Database databaseFromEnv() {
        String url = env("DB_URL", "jdbc:postgresql://localhost:5432/market");
        String user = env("DB_USER", "market");
        String password = env("DB_PASSWORD", "market");
        return new Database(url, user, password);
    }

    private static ExecutionConfig executionConfigFromEnv() {
        return new ExecutionConfig(
                doubleEnv("COMMISSION_PER_SHARE", 0.005),
                doubleEnv("SLIPPAGE_BPS", 1.0),
                booleanEnv("ALLOW_SHORTING", false),
                doubleEnv("MAX_ABSOLUTE_POSITION", 1_000.0));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static double doubleEnv(String name, double fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Double.parseDouble(value);
    }

    private static boolean booleanEnv(String name, boolean fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static void printResult(BacktestResult result) {
        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.US);
        System.out.printf("Events processed: %,d%n", result.eventsProcessed());
        System.out.printf("Runtime: %.3f seconds%n", result.runtime().toNanos() / 1_000_000_000.0);
        System.out.printf("Throughput: %,.0f events/sec%n", result.benchmarkStats().eventsPerSecond());
        System.out.printf("Latency nanos avg/p50/p95/p99: %.0f / %d / %d / %d%n",
                result.benchmarkStats().averageLatencyNanos(),
                result.benchmarkStats().p50LatencyNanos(),
                result.benchmarkStats().p95LatencyNanos(),
                result.benchmarkStats().p99LatencyNanos());
        System.out.printf("Memory delta: %.2f MB%n", result.usedMemoryBytes() / 1024.0 / 1024.0);
        System.out.printf("Final equity: %s%n", money.format(result.finalEquity()));
        System.out.printf("PnL: %s%n", money.format(result.metrics().pnl()));
        System.out.printf("Sharpe: %.4f%n", result.metrics().sharpeRatio());
        System.out.printf("Annualized volatility: %.4f%n", result.metrics().annualizedVolatility());
        System.out.printf("Sortino: %.4f%n", result.metrics().sortinoRatio());
        System.out.printf("Calmar: %.4f%n", result.metrics().calmarRatio());
        System.out.printf("Max drawdown: %s (%.2f%%)%n",
                money.format(result.metrics().maxDrawdown()),
                result.metrics().maxDrawdownPercent() * 100.0);
        System.out.printf("Win rate: %.2f%%%n", result.metrics().winRate() * 100.0);
        System.out.printf("Average trade PnL: %s%n", money.format(result.metrics().averageTradePnl()));
        System.out.printf("Turnover: %s%n", money.format(result.metrics().turnover()));
        System.out.printf("Trades: %,d%n", result.trades().size());
        result.trades().stream().limit(10).forEach(trade -> System.out.printf(
                "%s %s %s qty=%.4f price=%.4f fee=%.4f slip=%.4f cash=%.2f position=%.4f%n",
                trade.timestamp(),
                trade.symbol(),
                trade.side(),
                trade.quantity(),
                trade.price(),
                trade.commission(),
                trade.slippage(),
                trade.cashAfter(),
                trade.positionAfter()));
    }

    private static void requireArgs(String[] args, int expected, String usage) {
        if (args.length < expected) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private static void usage() {
        System.out.println("""
                Market Simulator Backend

                Commands:
                  ingest <csv-path>              Load normalized events into PostgreSQL
                  replay-csv <csv-path> <ma|mm>  Replay CSV directly
                  replay-db <symbol|ALL> <ma|mm> Replay from PostgreSQL chronologically
                  benchmark <csv-path> <ma|mm>   Replay CSV and print runtime/memory metrics
                  benchmark-synthetic <events> <ma|mm> [symbols]
                                                  Generate and replay synthetic events
                  lob-demo <orders> [seed]        Run synthetic order flow through the
                                                  price-time-priority limit order book

                CSV columns:
                  symbol,timestamp,type,price,volume,bid_price,bid_size,ask_price,ask_size

                PostgreSQL env vars:
                  DB_URL, DB_USER, DB_PASSWORD

                Execution env vars:
                  COMMISSION_PER_SHARE, SLIPPAGE_BPS, ALLOW_SHORTING, MAX_ABSOLUTE_POSITION
                """);
    }
}
