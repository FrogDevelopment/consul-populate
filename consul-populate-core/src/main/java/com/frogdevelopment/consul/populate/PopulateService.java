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
    void populate();
}
