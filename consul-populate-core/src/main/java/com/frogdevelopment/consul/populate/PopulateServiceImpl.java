package com.frogdevelopment.consul.populate;

import static com.frogdevelopment.consul.populate.VertxUtils.toBlocking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.config.GlobalProperties;

import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.TxnError;
import io.vertx.ext.consul.TxnKVOperation;
import io.vertx.ext.consul.TxnKVVerb;
import io.vertx.ext.consul.TxnOperation;
import io.vertx.ext.consul.TxnRequest;

/**
 * @author Le Gall Benoît
 * @since 1.0.0
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
class PopulateServiceImpl implements PopulateService {

    private final ConsulClient consulClient;
    private final GlobalProperties globalProperties;
    private final DataImporter dataImporter;

    // Consul transaction API limit
    static final int MAX_OPERATIONS_PER_TXN = 64;

    // the brackets of the JSON array that is the body of a transaction
    private static final int EMPTY_BODY_BYTES = 2;

    @Override
    public void populate(final boolean dryRun) {
        try {
            toBlocking(consulClient.leaderStatus());
        } catch (final Exception e) {
            throw new IllegalStateException("Consul is not reachable/ready to be populate. Please check error logs", e);
        }

        final var kvPath = globalProperties.getKv().getPath();

        log.info("Retrieving data to export");
        // Importing data from configured type. Sorted by key: the transactions, and therefore what a failed run has
        // already applied, are the same on every run
        final var configsToImport = new TreeMap<String, String>();
        dataImporter.execute().forEach((key, value) -> configsToImport.put(kvPath + key, value));

        // collect all SET operations
        final var operations = new ArrayList<TxnOperation>();
        configsToImport.entrySet()
                .stream()
                .map(toSetOperation())
                .forEach(operations::add);

        // retrieve current configs in Consul KV and append DELETE operations for removed keys
        final List<String> keysToDelete;
        if (globalProperties.getKv().isPrune()) {
            keysToDelete = toBlocking(consulClient.getKeys(kvPath)).stream()
                    .filter(Predicate.not(configsToImport::containsKey))
                    .sorted()
                    .toList();
        } else {
            log.info("consul.kv.prune=false: keys under '{}' that are not in the source are kept", kvPath);
            keysToDelete = List.of();
        }
        keysToDelete.stream()
                .map(toDeleteOperation())
                .forEach(operations::add);

        // split before writing anything, so that a value that cannot be sent fails the run (and the dry run) up front
        final var transactions = toTransactions(operations, globalProperties.getKv().getMaxTxnBytes());
        final var txnCount = transactions.size();
        if (dryRun) {
            logPlan(configsToImport.keySet(), keysToDelete, txnCount);
            return;
        }
        if (!keysToDelete.isEmpty()) {
            // everything under the prefix that is not in the source goes: make it visible, it may not be intended
            log.warn("{} key(s) under '{}' are not in the source and will be deleted (consul.kv.prune=false keeps them)",
                    keysToDelete.size(), kvPath);
        }
        log.info("Exporting data to consul ({} operations in {} transaction(s))", operations.size(), txnCount);

        for (var i = 0; i < txnCount; i++) {
            final var result = toBlocking(consulClient.transaction(transactions.get(i)));
            final var txnNumber = i + 1;
            if (result.getErrorsSize() > 0) {
                // each transaction is atomic, but earlier ones are already committed: stop rather than leave more gaps
                throw new IllegalStateException("Transaction %d/%d failed, %d operation(s) in error:%n\t- %s".formatted(
                        txnNumber,
                        txnCount,
                        result.getErrorsSize(),
                        result.getErrors()
                                .stream()
                                .map(TxnError::getWhat)
                                .collect(Collectors.joining("\n\t- "))));
            }
            log.info("Transaction {}/{} succeeded: {} result(s)", txnNumber, txnCount, result.getResultsSize());
        }
    }

    /**
     * Splits the operations in transactions that Consul accepts: at most {@link #MAX_OPERATIONS_PER_TXN} operations, and
     * a request body of at most {@code maxBytes} (Consul's {@code txn_max_req_len}, 512 KiB by default, answers 413
     * above). The body is the exact JSON the client sends, so the size is measured, not estimated.
     *
     * @throws IllegalStateException if a single operation is larger than {@code maxBytes}: it could never be sent
     */
    static List<TxnRequest> toTransactions(final List<TxnOperation> operations, final long maxBytes) {
        final var transactions = new ArrayList<TxnRequest>();
        var current = new TxnRequest();
        var currentCount = 0;
        var currentBytes = EMPTY_BODY_BYTES;

        for (final var operation : operations) {
            final var operationBytes = encodedSize(operation);
            if (EMPTY_BODY_BYTES + operationBytes > maxBytes) {
                throw new IllegalStateException(("The key '%s' is too large for a Consul transaction: %d bytes once "
                        + "encoded, the limit is %d (Consul's txn_max_req_len, see consul.kv.max-txn-bytes)")
                        .formatted(((TxnKVOperation) operation).getKey(), EMPTY_BODY_BYTES + operationBytes, maxBytes));
            }

            // operations are separated by a comma
            final var addedBytes = currentCount == 0 ? operationBytes : operationBytes + 1;
            if (currentCount > 0 && (currentCount == MAX_OPERATIONS_PER_TXN || currentBytes + addedBytes > maxBytes)) {
                transactions.add(current);
                current = new TxnRequest();
                currentCount = 0;
                currentBytes = EMPTY_BODY_BYTES;
            }
            currentBytes += currentCount == 0 ? operationBytes : operationBytes + 1;
            current.addOperation(operation);
            currentCount++;
        }

        if (currentCount > 0) {
            transactions.add(current);
        }
        return transactions;
    }

    /**
     * @return the size of the request body of a transaction, as the client builds it
     */
    static int bodySize(final TxnRequest request) {
        return request.toJson().getJsonArray("operations").encode().getBytes(StandardCharsets.UTF_8).length;
    }

    private static int encodedSize(final TxnOperation operation) {
        return bodySize(new TxnRequest().addOperation(operation)) - EMPTY_BODY_BYTES;
    }

    private static void logPlan(final Collection<String> keysToSet, final Collection<String> keysToDelete,
                                final int txnCount) {
        log.info("Dry run, nothing is written to Consul: {} key(s) to set, {} key(s) to delete, in {} transaction(s)",
                keysToSet.size(), keysToDelete.size(), txnCount);
        new TreeSet<>(keysToSet).forEach(key -> log.info("  SET    {}", key));
        keysToDelete.stream().sorted().forEach(key -> log.info("  DELETE {}", key));
    }

    private static Function<Map.Entry<String, String>, TxnKVOperation> toSetOperation() {
        return entry -> new TxnKVOperation()
                .setKey(entry.getKey())
                .setValue(entry.getValue())
                .setType(TxnKVVerb.SET);
    }

    private static Function<String, TxnKVOperation> toDeleteOperation() {
        return key -> new TxnKVOperation()
                .setKey(key)
                .setType(TxnKVVerb.DELETE);
    }

}
