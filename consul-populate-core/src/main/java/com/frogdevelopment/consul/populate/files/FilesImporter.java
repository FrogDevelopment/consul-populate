package com.frogdevelopment.consul.populate.files;

import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
abstract sealed class FilesImporter implements DataImporter
        permits JsonFilesImporter, PropertiesFilesImporter, YamlFilesImporter {

    private static final Logger log = LoggerFactory.getLogger(FilesImporter.class);
    private final ImportFileProperties importProperties;

    @NonNull
    @Override
    public Map<String, String> execute() {
        // validate paths
        final var rootPath = Paths.get(importProperties.getRootPath());
        if (!rootPath.toFile().exists()) {
            throw new IllegalArgumentException("Root directory does not exist: " + rootPath);
        }
        final var targetPath = rootPath.resolve(importProperties.getTarget());
        if (!targetPath.toFile().exists()) {
            throw new IllegalArgumentException("Target directory does not exist: " + targetPath);
        }

        // list files in root directory
        final var rootFiles = getRootFiles(rootPath);

        // list files in target subdirectory
        final var targetFiles = getTargetFiles(targetPath);

        // for each target, merge in root if exists
        final var merged = MapHelper.merge(rootFiles, targetFiles);

        try {
            final var result = new HashMap<String, String>();
            for (final var entry : merged.entrySet()) {
                final var key = FilenameUtils.removeExtension(entry.getKey());
                final var value = writeValueAsString(entry.getValue());
                result.put(key, value);
            }

            return result;
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to process the configurations to import. Please check the error logs", e);
        }
    }

    private Map<String, SequencedMap<String, Object>> getRootFiles(final Path rootPath) {
        try {
            final var rootFiles = rootPath.toFile().listFiles(this::filterFile);
            if (ArrayUtils.isEmpty(rootFiles)) {
                throw new IllegalArgumentException("No configuration files found in root directory: " + rootPath);
            }
            final var rootFilesMap = new HashMap<String, SequencedMap<String, Object>>();
            for (final var rootFile : rootFiles) {
                rootFilesMap.put(rootFile.getName(), readFile(rootFile));
            }
            return rootFilesMap;
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to read the root configuration. Please check the error logs", e);
        }
    }

    private Map<String, SequencedMap<String, Object>> getTargetFiles(final Path targetPath) {
        try {
            final var targetFiles = targetPath.toFile().listFiles(this::filterFile);
            if (ArrayUtils.isEmpty(targetFiles)) {
                throw new IllegalArgumentException("No configuration files found in target directory: " + targetPath);
            }
            final var targetFilesMap = new HashMap<String, SequencedMap<String, Object>>();
            for (final var targetFile : targetFiles) {
                final var target = readFile(targetFile);
                if (target == null) {
                    log.warn("Content is null for file: {}", targetFile.getAbsolutePath());
                    continue;
                }
                targetFilesMap.put(targetFile.getName(), target);
            }
            return targetFilesMap;
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to read the target configurations. Please check the error logs", e);
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
