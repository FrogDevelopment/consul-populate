package com.frogdevelopment.consul.populate.files;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SequencedMap;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * Implementation for Properties files import
 *
 * @author Le Gall Benoît
 * @see Properties
 * @since 1.0.0
 */
public final class PropertiesFilesImporter extends FilesImporter {

    private static final List<String> EXTENSIONS = List.of("properties");

    /**
     * Constructor
     *
     * @param rootPath     Path to root directory
     * @param targetPath   Subdirectory used to override root configurations
     */
    public PropertiesFilesImporter(final Path rootPath, final Path targetPath) {
        super(rootPath, targetPath);
    }

    @Override
    protected boolean isExtensionAccepted(@NonNull final String extension) {
        return EXTENSIONS.contains(extension.toLowerCase());
    }

    @Nullable
    @Override
    protected SequencedMap<String, Object> readFile(@NonNull final File file) throws IOException {
        try (final var reader = Files.newBufferedReader(file.toPath())) {
            final var properties = new Properties();
            properties.load(reader);
            return properties.entrySet()
                    .stream()
                    .collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue, (x, y) -> y, LinkedHashMap::new));
        }
    }

    @NonNull
    @Override
    protected String writeValueAsString(@NonNull final Map<String, Object> map) throws IOException {
        if (map.isEmpty()) {
            return "";
        }
        final var properties = new Properties();
        map.forEach((k, v) -> properties.setProperty(k, v.toString()));
        final var sw = new StringWriter();
        properties.store(sw, null);
        // Properties.store() prepends a "#<timestamp>" comment line and a blank line — strip both
        return sw.toString().lines()
                .filter(line -> !line.startsWith("#"))
                .collect(Collectors.joining("\n"))
                .strip();
    }
}
