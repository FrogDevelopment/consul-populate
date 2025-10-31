package com.frogdevelopment.consul.populate.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SequencedMap;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * Implementation for Properties files import
 *
 * @author Le Gall Benoît
 * @see Properties
 * @since 1.0.0
 */
@Singleton
@Requires(property = "consul.files.format", value = "PROPERTIES")
public final class PropertiesFilesImporter extends FilesImporter {

    private static final List<String> EXTENSIONS = List.of("properties");

    /**
     * Constructor
     *
     * @param importProperties Properties for the import
     */
    public PropertiesFilesImporter(final ImportFileProperties importProperties) {
        super(importProperties);
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
    protected String writeValueAsString(@NonNull final Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return map.entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }
}
