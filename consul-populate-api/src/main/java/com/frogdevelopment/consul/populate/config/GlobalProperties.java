package com.frogdevelopment.consul.populate.config;

import lombok.Data;

import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;

@Data
@ConfigurationProperties("consul")
public class GlobalProperties {

    /**
     * Consul host. Defaults to {@code localhost}
     */
    private String host = "localhost";
    /**
     * Consul port. Defaults to {@code 8500}
     */
    private int port = 8500;
    /**
     * Set whether Consul is secured. Defaults to {@code false}
     */
    private boolean secured = false;
    /**
     * ACL token needed to read and write in KV path. When present, will be added to requests using `?token` query parameter
     */
    private Optional<String> aclToken = Optional.empty();
    /**
     * Consul datacenter name. When present, will be added to requests using `?dc` query parameter
     */
    private Optional<String> dc = Optional.empty();
    /**
     * Amount of time (in milliseconds) for requests
     */
    private Optional<Long> timeout = Optional.empty();
    /**
     * The path where the configuration is stored. Will be used to generate the `configPath`. Defaults to {@code config}
     */
    private String configPathPrefix = "config";
    /**
     * Version of the current configuration. When present, will be used to generate the `configPath`
     */
    private Optional<String> configVersion;
    /**
     * Type of data used to be export into Consul
     */
    private Type type;

    /**
     * @return Full config path by concatenating {@code configPathPrefix} & {@code configVersion} if present
     */
    public String getConfigPath() {
        if (configPathPrefix.endsWith("/")) {
            return configPathPrefix + configVersion.orElse("");
        } else {
            return configPathPrefix + '/' + configVersion.orElse("");
        }
    }

    enum Type {
        FILES, GIT
    }
}
