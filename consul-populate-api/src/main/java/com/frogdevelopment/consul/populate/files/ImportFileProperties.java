package com.frogdevelopment.consul.populate.files;

import lombok.Data;

import com.frogdevelopment.consul.populate.config.ImportProperties;

import io.micronaut.context.annotation.ConfigurationProperties;

@Data
@ConfigurationProperties("consul.files")
public final class ImportFileProperties implements ImportProperties {

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
