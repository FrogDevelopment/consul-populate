package com.frogdevelopment.consul.populate;

import java.util.Map;

import io.micronaut.core.annotation.NonNull;

/**
 * Interface to extend with the specific import type implementation
 *
 * @author Le Gall Benoît
 * @since 1.0.0
 */
public interface DataImporter {

    /**
     * Do the import from the data type
     *
     * @return Map of key-value to be imported into Consul's KV
     */
    @NonNull
    Map<String, String> execute();

}
