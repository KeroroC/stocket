package com.stocket.system.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.stocket.catalog.CatalogExportQuery;
import com.stocket.catalog.CatalogExportRow;
import com.stocket.catalog.CatalogFilter;
import com.stocket.inventory.InventoryExportQuery;
import com.stocket.inventory.InventoryExportRow;
import com.stocket.inventory.InventoryFilter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class CsvExportService {
    static final int LIMIT = 100_000;
    private static final int BATCH_SIZE = 1_000;
    private final CatalogExportQuery catalog;
    private final InventoryExportQuery inventory;
    private final TransactionTemplate transaction;

    CsvExportService(CatalogExportQuery catalog, InventoryExportQuery inventory,
                     PlatformTransactionManager transactionManager) {
        this.catalog = catalog;
        this.inventory = inventory;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setReadOnly(true);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    void assertCatalogLimit(UUID householdId, CatalogFilter filter) {
        if (catalog.countForExport(householdId, filter) > LIMIT) throw limitExceeded();
    }

    void assertInventoryLimit(UUID householdId, InventoryFilter filter) {
        if (inventory.countForExport(householdId, filter) > LIMIT) throw limitExceeded();
    }

    void writeCatalog(UUID householdId, CatalogFilter filter, OutputStream output) throws IOException {
        execute(() -> {
            OutputStreamWriter writer = writer(output);
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                    .setHeader("id", "name", "categoryPath", "brand", "model", "specification", "tags", "barcodes").get())) {
                UUID afterId = null;
                int written = 0;
                while (true) {
                    List<CatalogExportRow> rows = catalog.exportPage(householdId, filter, afterId, BATCH_SIZE);
                    for (CatalogExportRow row : rows) {
                        printer.printRecord(cell(row.id()), cell(row.name()), cell(row.categoryPath()), cell(row.brand()),
                                cell(row.model()), cell(row.specification()), cell(String.join("|", row.tags())),
                                cell(String.join("|", row.barcodes())));
                        afterId = row.id();
                        if (++written > LIMIT) throw limitExceeded();
                    }
                    if (rows.size() < BATCH_SIZE) break;
                }
            }
        });
    }

    void writeInventory(UUID householdId, InventoryFilter filter, OutputStream output) throws IOException {
        execute(() -> {
            OutputStreamWriter writer = writer(output);
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                    .setHeader("id", "itemId", "itemName", "locationId", "locationName", "type", "availableQuantity",
                            "receivedAt", "productionDate", "expirationDate", "batchNumber", "assetNumber", "serialNumber",
                            "assetStatus", "archived").get())) {
                UUID afterId = null;
                int written = 0;
                while (true) {
                    List<InventoryExportRow> rows = inventory.exportPage(householdId, filter, afterId, BATCH_SIZE);
                    for (InventoryExportRow row : rows) {
                        printer.printRecord(cell(row.id()), cell(row.itemId()), cell(row.itemName()), cell(row.locationId()),
                                cell(row.locationName()), cell(row.type()), cell(row.availableQuantity()), cell(row.receivedAt()),
                                cell(row.productionDate()), cell(row.expirationDate()), cell(row.batchNumber()), cell(row.assetNumber()),
                                cell(row.serialNumber()), cell(row.assetStatus()), cell(row.archived()));
                        afterId = row.id();
                        if (++written > LIMIT) throw limitExceeded();
                    }
                    if (rows.size() < BATCH_SIZE) break;
                }
            }
        });
    }

    private OutputStreamWriter writer(OutputStream output) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writer.write('\ufeff');
        return writer;
    }

    private void execute(IoAction action) throws IOException {
        try {
            transaction.executeWithoutResult(status -> {
                try { action.run(); } catch (IOException error) { throw new UncheckedIOException(error); }
            });
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
    }

    private String cell(Object value) { return CsvCellSanitizer.sanitize(value); }
    private ExportProblem limitExceeded() { return new ExportProblem(HttpStatus.UNPROCESSABLE_ENTITY, "EXPORT_LIMIT_EXCEEDED"); }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
}
