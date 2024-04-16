package com.frogdevelopment.consul.populate.config;

import lombok.Data;

import io.micronaut.context.annotation.ConfigurationProperties;

@Data
@ConfigurationProperties("consul")
public class ConsulGlobalProperties {

    /**
     * Address of the Consul to populate
     */
    private String httpAddress;

    /**
     * The path where the configuration is stored. Default to `config`
     */
    private String configPath = "config";

    /**
     * Version of the current configuration. Will be added to the `configPath`
     */
    private String version;

    private Type type;

    enum Type {
        FILES, GIT
    }
}
