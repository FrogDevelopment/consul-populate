package com.frogdevelopment.consul.populate;

/**
 * Entry point to run the data import
 *
 * @author Le Gall Benoît
 * @since 1.0.0
 */
public interface PopulateService {

    /**
     * Main method that is going to do the defined import into Consul's KV
     */
    default void populate() {
        populate(false);
    }

    /**
     * Runs the defined import into Consul's KV, or only validates it.
     *
     * <p>A dry run does everything except the write: Consul must be reachable, the data is read and parsed, the keys
     * currently in Consul are listed, and the resulting SET and DELETE operations are logged. No transaction is sent.
     *
     * @param dryRun {@code true} to build and log the operations without writing them
     */
    void populate(boolean dryRun);
}
