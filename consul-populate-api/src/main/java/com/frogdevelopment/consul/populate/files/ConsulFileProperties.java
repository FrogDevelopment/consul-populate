package com.frogdevelopment.consul.populate.files;

import lombok.Data;

import io.micronaut.context.annotation.ConfigurationProperties;

@Data
@ConfigurationProperties("consul.files")
public class ConsulFileProperties {

    /**
     * Path to root directory
     */
    private String rootPath;

    /**
     * Subdirectory used to override root configurations
     */
    private String target;

    private Format format = Format.YAML;

    public enum Format {
        YAML,
        JSON,
        PROPERTIES
    }
}
