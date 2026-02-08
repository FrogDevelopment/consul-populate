package com.frogdevelopment.consul.populate.files;

import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.SequencedMap;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.frogdevelopment.consul.populate.DataImporter;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArrayUtils;

/**
 * Base logic when importing data from files
 *
 * @author Le Gall Benoît
 * @since 1.0.0
 */
@RequiredArgsConstructor
public abstract sealed class FilesImporter implements DataImporter
        permits JsonFilesImporter, PropertiesFilesImporter, YamlFilesImporter {

    private static final Logger log = LoggerFactory.getLogger(FilesImporter.class);

    private final Path rootPath;
    private final Path targetPath;

    @NonNull
    @Override
    public Map<String, String> execute() {
        // validate paths
        if (!rootPath.toFile().exists()) {
            throw new IllegalArgumentException("Root directory does not exist: " + rootPath);
        }
        if (!targetPath.toFile().exists()) {
            throw new IllegalArgumentException("Target directory does not exist: " + targetPath);
        }

        // list files in root directory
        final var rootFiles = readFiles(rootPath);

        final Map<String, SequencedMap<String, Object>> finalFiles;
        if (rootPath.equals(targetPath)) {
            finalFiles = rootFiles;
        } else {
            // list files in target subdirectory
            final var targetFiles = readFiles(targetPath);

            // for each target, merge in root if exists
            finalFiles = MapHelper.merge(rootFiles, targetFiles);
        }

        try {
            final var result = new HashMap<String, String>();
            for (final var entry : finalFiles.entrySet()) {
                final var key = FilenameUtils.removeExtension(entry.getKey());
                final var value = writeValueAsString(entry.getValue());
                if (value.isEmpty()) {
                    log.warn("Skipping empty configurations for file '{}'", entry.getKey());
                } else {
                    result.put(key, value);
                }
            }

            return result;
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to process the configurations to import. Please check the error logs", e);
        }
    }

    private Map<String, SequencedMap<String, Object>> readFiles(final Path path) {
        try {
            final var files = path.toFile().listFiles(this::filterFile);
            if (ArrayUtils.isEmpty(files)) {
                throw new IllegalArgumentException("No configuration files found in directory: " + path);
            }
            final var dataMap = new HashMap<String, SequencedMap<String, Object>>();
            for (final var file : files) {
                final var data = readFile(file);
                if (data == null) {
                    log.warn("Content is null for file: {}", file.getAbsolutePath());
                    continue;
                }
                dataMap.put(file.getName(), data);
            }
            return dataMap;
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to read the configurations. Please check the error logs", e);
        }
    }

    private boolean filterFile(@NonNull final File file) {
        if (file.isDirectory()) {
            return false;
        }
        final var extension = FilenameUtils.getExtension(file.getName());
        return isExtensionAccepted(extension);
    }

    /**
     * @param extension File extension
     * @return {@code true} if the extension is supported
     */
    protected abstract boolean isExtensionAccepted(@NonNull final String extension);

    @Nullable
    protected abstract SequencedMap<String, Object> readFile(@NonNull final File file) throws IOException;

    @NonNull
    protected abstract String writeValueAsString(@NonNull final Map<String, Object> map) throws IOException;
}
