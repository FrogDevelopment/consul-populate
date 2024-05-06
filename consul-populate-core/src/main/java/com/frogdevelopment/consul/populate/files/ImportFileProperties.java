package com.frogdevelopment.consul.populate.files;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.frogdevelopment.consul.populate.config.ImportProperties;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Properties for files import
 *
 * @author Le Gall Benoît
 * @since 1.0.0
 */
@Data
@ConfigurationProperties("consul.files")
public final class ImportFileProperties implements ImportProperties {

    /**
     * Path to root directory
     */
    @NotBlank
    private String rootPath;

    /**
     * Subdirectory used to override root configurations
     */
    @NotBlank
    private String target;

    /**
     * Format of files to import
     */
    @NotNull
    private Format format = Format.YAML;

    /**
     * Supported type of file format
     */
    public enum Format {
        YAML,
        JSON,
        PROPERTIES
    }
}
