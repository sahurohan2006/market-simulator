package com.market.backend.csv;

import com.market.backend.db.Database;
import com.market.backend.db.MarketEventRepository;

import java.nio.file.Path;
import java.sql.Connection;

public final class CsvIngestionService {
    private final Database database;
    private final MarketEventRepository repository;

    public CsvIngestionService(Database database, MarketEventRepository repository) {
        this.database = database;
        this.repository = repository;
    }

    public long ingest(Path csvPath) throws Exception {
        database.migrate();
        try (Connection connection = database.connect();
             CsvMarketEventReader reader = new CsvMarketEventReader(csvPath)) {
            return repository.insert(connection, reader, csvPath.getFileName().toString());
        }
    }
}
