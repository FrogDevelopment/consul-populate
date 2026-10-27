package com.frogdevelopment.consul.populate.files;

import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.frogdevelopment.consul.populate.DataImporter;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

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

    /** When set, only the files that resolve inside this directory are read; {@code null} reads everything. */
    private Path confinedTo;

    /**
     * Restricts the import to the files that really live inside {@code directory}: a symbolic link that points outside
     * is ignored. Use it when the content is not trusted, for instance a cloned repository where anyone with push access
     * could otherwise commit {@code application.yaml -> /path/on/the/server} and have the file published to Consul.
     * Links that stay inside the directory are followed. It is not applied by default, because the configuration
     * directory of a Kubernetes ConfigMap or Secret is made of symbolic links.
     *
     * @param directory the directory the files must resolve into
     */
    public void confineTo(final Path directory) {
        this.confinedTo = directory;
    }

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
            final var fileOfKey = new HashMap<String, String>();
            for (final var entry : finalFiles.entrySet()) {
                final var key = FilenameUtils.removeExtension(entry.getKey());
                final var value = writeValueAsString(entry.getValue());
                if (value.isEmpty()) {
                    log.warn("Skipping empty configurations for file '{}'", entry.getKey());
                } else {
                    final var other = fileOfKey.put(key, entry.getKey());
                    if (other != null) {
                        // the extension is not part of the key: one of the two would silently replace the other
                        throw new IllegalArgumentException("The files '%s' and '%s' both map to the key '%s': keep only one of them"
                                .formatted(Stream.of(other, entry.getKey()).sorted().toList().get(0),
                                        Stream.of(other, entry.getKey()).sorted().toList().get(1), key));
                    }
                    result.put(key, value);
                }
            }

            if (result.isEmpty()) {
                // importing nothing would delete everything under the KV path
                throw new IllegalStateException("There is nothing to import: every configuration file in " + rootPath
                        + (rootPath.equals(targetPath) ? "" : " and " + targetPath) + " is empty");
            }
            return result;
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to process the configurations to import. Please check the error logs", e);
        }
    }

    private Map<String, SequencedMap<String, Object>> readFiles(final Path path) {
        try {
            final var files = path.toFile().listFiles(this::filterFile);
            if (files == null) {
                throw new IllegalArgumentException("Not a directory or unreadable: " + path);
            }
            if (files.length == 0) {
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
            throw new IllegalStateException("Unable to read the configurations: " + e.getMessage(), e);
        }
    }

    private boolean filterFile(@NonNull final File file) {
        if (file.isDirectory()) {
            return false;
        }
        final var extension = FilenameUtils.getExtension(file.getName());
        return isExtensionAccepted(extension) && isInsideConfinement(file);
    }

    private boolean isInsideConfinement(final File file) {
        if (confinedTo == null) {
            return true;
        }
        try {
            if (file.toPath().toRealPath().startsWith(confinedTo.toRealPath())) {
                return true;
            }
            log.warn("Ignoring '{}': it resolves outside of {}", file, confinedTo);
        } catch (final IOException e) {
            log.warn("Ignoring '{}': it cannot be resolved ({})", file, e.toString());
        }
        return false;
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
