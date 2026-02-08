package com.frogdevelopment.consul.populate.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * Implementation for JSON files import
 *
 * @author Le Gall Benoît
 * @see ObjectMapper
 * @since 1.0.0
 */
public final class JsonFilesImporter extends FilesImporter {

    private static final List<String> EXTENSIONS = List.of("json");

    private final ObjectMapper objectMapper;

    /**
     * Constructor
     *
     * @param rootPath     Path to root directory
     * @param targetPath   Subdirectory used to override root configurations
     * @param objectMapper ObjectMapper instance used for Json I/O
     */
    public JsonFilesImporter(final Path rootPath, final Path targetPath, final ObjectMapper objectMapper) {
        super(rootPath, targetPath);
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
        if (map == null || map.isEmpty()) {
            return "";
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
    }
}
