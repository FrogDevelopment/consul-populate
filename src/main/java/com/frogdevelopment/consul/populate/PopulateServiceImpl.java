package com.frogdevelopment.consul.populate;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Predicate;

import com.frogdevelopment.consul.populate.config.ConsulGlobalProperties;

import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.TxnKVOperation;
import io.vertx.ext.consul.TxnKVVerb;
import io.vertx.ext.consul.TxnRequest;

@Slf4j
@RequiredArgsConstructor
public class PopulateServiceImpl {

    private final ConsulClient consulClient;
    private final ConsulGlobalProperties consulGlobalProperties;
    private final DataImporter dataImporter;

    @SneakyThrows // temporary
    public void populate() {
        log.info("### PUSHING DATA IN KV STORAGE for version=$KV_VERSION and target=$CONF_TARGET");

        // 1. check Consul connection ?
//        consulClient.healthState()

        // Importing data from configured type
        var configToImport = dataImporter.execute();

        // Create/Update/Delete config
        final var txnRequest = new TxnRequest();
        configToImport.entrySet()
                .stream()
                .map(entry -> new TxnKVOperation()
                        .setKey(entry.getKey())
                        .setValue(entry.getValue())
                        .setType(TxnKVVerb.SET))
                .forEach(txnRequest::addOperation);

        // retrieve current config in Consul KV
        final var configPath = consulGlobalProperties.getConfigPath();
        final var version = consulGlobalProperties.getVersion();
        var existingKeysInConsul = consulClient.getKeys(configPath + "/" + version)
                .toCompletionStage()
                .toCompletableFuture()
                .get();

        existingKeysInConsul.stream()
                .filter(Predicate.not(configToImport::containsKey))
                .map(key -> new TxnKVOperation()
                        .setKey(key)
                        .setType(TxnKVVerb.DELETE))
                .forEach(txnRequest::addOperation);

        consulClient.transaction(txnRequest).onComplete(res -> {
            if (res.succeeded()) {
                final var result = res.result();
                log.info("succeeded results size: {}", result.getResultsSize());
                log.info("errors size: {}", result.getErrorsSize());
                if (result.getErrorsSize() > 0) {
                    result.getErrors().forEach(txnError -> log.error("error: {}", txnError.getWhat()));
                }
            } else {
                log.error(res.cause().getMessage(), res.cause());
            }
        });
    }
}
