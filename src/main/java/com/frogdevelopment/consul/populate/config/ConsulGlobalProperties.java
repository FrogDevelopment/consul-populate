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

    private String version;

    private String configPath;

    private Type type;

    enum Type {
        FILES, GIT
    }
}
