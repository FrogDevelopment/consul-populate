package com.frogdevelopment.consul.populate.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * Implementation for JSON files import
 *
 * @author Le Gall Benoît
 * @see ObjectMapper
 * @since 1.0.0
 */
@Singleton
@Requires(property = "consul.files.format", value = "JSON")
public final class JsonFilesImporter extends FilesImporter {

    private static final List<String> EXTENSIONS = List.of("json");

    private final ObjectMapper objectMapper;

    /**
     * Constructor
     *
     * @param importProperties Properties for the import
     * @param objectMapper     ObjectMapper instance used for Json I/O
     */
    public JsonFilesImporter(final ImportFileProperties importProperties, final ObjectMapper objectMapper) {
        super(importProperties);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean isExtensionAccepted(@NonNull final String extension) {
        return EXTENSIONS.contains(extension.toLowerCase());
    }

    @Nullable
    @Override
    protected SequencedMap<String, Object> readFile(@NonNull final File file) throws IOException {
        try (final var reader = Files.newBufferedReader(file.toPath())) {
            final var typeRef = new TypeReference<LinkedHashMap<String, Object>>() {
            };
            return objectMapper.readValue(reader, typeRef);
        }
    }

    @NonNull
    @Override
    protected String writeValueAsString(@NonNull final Map<String, Object> map) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
    }
}
