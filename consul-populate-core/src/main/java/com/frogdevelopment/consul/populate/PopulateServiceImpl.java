package com.frogdevelopment.consul.populate;

import static com.frogdevelopment.consul.populate.VertxUtils.toBlocking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.config.GlobalProperties;

import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.TxnError;
import io.vertx.ext.consul.TxnKVOperation;
import io.vertx.ext.consul.TxnKVVerb;
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

    @Override
    public void populate() {
        try {
            toBlocking(consulClient.leaderStatus());
        } catch (final Exception e) {
            throw new IllegalStateException("Consul is not reachable/ready to be populate. Please check error logs", e);
        }

        final var kvPath = globalProperties.getKv().getPath();

        log.info("Retrieving data to export");
        // Importing data from configured type
        final var configsToImport = dataImporter.execute()
                .entrySet()
                .stream().collect(Collectors.toMap(entry -> kvPath + entry.getKey(), Map.Entry::getValue));

        // Create/Update/Delete config
        final var txnRequest = new TxnRequest();
        configsToImport.entrySet()
                .stream()
                .map(toSetOperation())
                .forEach(txnRequest::addOperation);

        // retrieve current configs in Consul KV
        final var existingKeysInConsul = toBlocking(consulClient.getKeys(kvPath));

        // keep only those that are to be deleted (no present anymore in the data pushed)
        existingKeysInConsul.stream()
                .filter(Predicate.not(configsToImport::containsKey))
                .map(toDeleteOperation())
                .forEach(txnRequest::addOperation);

        log.info("Exporting data to consul");
        final var result = toBlocking(consulClient.transaction(txnRequest));
        log.info("succeeded results size: {}", result.getResultsSize());
        if (result.getErrorsSize() > 0) {
            log.error("Some operations ({}) lead to error:{}",
                    result.getErrorsSize(),
                    result.getErrors()
                            .stream()
                            .map(TxnError::getWhat)
                            .collect(Collectors.joining("\n\t- "))
            );
        }
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
