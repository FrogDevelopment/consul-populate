package com.frogdevelopment.consul.populate.files;

import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Factory handling Files import based on the {@link ImportFileProperties.Format} defined in configuration.
 */
@Factory
@Requires(property = "consul.files")
public class FilesImportFactory {

    @Singleton
    FilesImporter filesImporter(final ImportFileProperties properties, final ObjectMapper objectMapper) {
        final var rootPath = Paths.get(properties.getRootPath());
        final var targetPath = rootPath.resolve(properties.getTarget());
        return switch (properties.getFormat()) {
            case JSON -> new JsonFilesImporter(rootPath, targetPath, objectMapper);
            case PROPERTIES -> new PropertiesFilesImporter(rootPath, targetPath);
            case YAML -> new YamlFilesImporter(rootPath, targetPath);
        };
    }
}
