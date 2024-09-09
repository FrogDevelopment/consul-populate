package com.frogdevelopment.consul.populate.files;

import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frogdevelopment.consul.populate.DataImporter;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

/**
 * Factory handling Files import based on the {@link ImportFileProperties.Format} defined in configuration.
 */
@Factory
@Requires(property = "consul.files")
public class FilesImportFactory {

    @Bean
    @Requires(property = "consul.files.format", value = "JSON")
    DataImporter jsonFilesImporter(final ImportFileProperties importFileProperties, final ObjectMapper objectMapper) {
        final var rootPath = Paths.get(importFileProperties.getRootPath());
        final var targetPath = rootPath.resolve(importFileProperties.getTarget());
        return new JsonFilesImporter(rootPath, targetPath, objectMapper);
    }

    @Bean
    @Requires(property = "consul.files.format", value = "PROPERTIES")
    DataImporter propertiesFilesImporter(final ImportFileProperties importFileProperties) {
        final var rootPath = Paths.get(importFileProperties.getRootPath());
        final var targetPath = rootPath.resolve(importFileProperties.getTarget());
        return new PropertiesFilesImporter(rootPath, targetPath);
    }

    @Bean
    @Requires(property = "consul.files.format", value = "YAML")
    DataImporter yamlFilesImporter(final ImportFileProperties importFileProperties) {
        final var rootPath = Paths.get(importFileProperties.getRootPath());
        final var targetPath = rootPath.resolve(importFileProperties.getTarget());
        return new YamlFilesImporter(rootPath, targetPath);
    }
}
