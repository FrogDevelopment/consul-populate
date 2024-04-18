package com.frogdevelopment.consul.populate.config;

import lombok.Data;

import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;

@Data
@ConfigurationProperties("consul")
public class ConsulGlobalProperties {

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
    private boolean isSecured = false;
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
     * Version of the current configuration. Will be used to generate the `configPath`
     */
    private String version;
    /**
     * Type of data used to be export into Consul
     */
    private Type type;

    /**
     * @return Full config path by concatenating {@code configPathPrefix} & {@code version}
     */
    public String getConfigPath() {
        if (configPathPrefix.endsWith("/")) {
            return configPathPrefix + version;
        } else {
            return configPathPrefix + '/' + version;
        }
    }

    enum Type {
        FILES, GIT
    }
}
