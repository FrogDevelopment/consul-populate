package com.frogdevelopment.consul.populate.git;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frogdevelopment.consul.populate.DataImporter;
import com.frogdevelopment.consul.populate.files.JsonFilesImporter;
import com.frogdevelopment.consul.populate.files.PropertiesFilesImporter;
import com.frogdevelopment.consul.populate.files.YamlFilesImporter;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;

/**
 * Git-based data importer that reads configuration files from a cloned repository.
 * Delegates to the appropriate {@link com.frogdevelopment.consul.populate.files.FilesImporter}
 * based on the configured file format.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class GitImporter implements DataImporter {

    private final RepositoryDirectoryProvider repositoryDirectoryProvider;
    private final GitProperties gitProperties;
    private final ObjectMapper objectMapper;

    /**
     * Reads configuration files from the cloned git repository and converts them to key-value pairs.
     *
     * @return map of configuration keys to their JSON/YAML/Properties string values
     */
    @NonNull
    @Override
    public Map<String, String> execute() {
        final var repositoryDirectory = repositoryDirectoryProvider.getRepository();
        log.debug("Reading configurations from git repository at {}", repositoryDirectory);

        final var fileProperties = gitProperties.getFileProperties();

        // Calculate root and target paths within the cloned repository
        final var rootPath = StringUtils.isEmpty(fileProperties.getRootPath())
                ? repositoryDirectory
                : repositoryDirectory.resolve(fileProperties.getRootPath());

        final var targetPath = StringUtils.isEmpty(fileProperties.getTarget())
                ? rootPath
                : rootPath.resolve(fileProperties.getTarget());

        // Delegate to the appropriate FilesImporter based on format
        final DataImporter filesImporter = switch (fileProperties.getFormat()) {
            case JSON -> new JsonFilesImporter(rootPath, targetPath, objectMapper);
            case PROPERTIES -> new PropertiesFilesImporter(rootPath, targetPath);
            case YAML -> new YamlFilesImporter(rootPath, targetPath);
        };

        return filesImporter.execute();
    }
}
