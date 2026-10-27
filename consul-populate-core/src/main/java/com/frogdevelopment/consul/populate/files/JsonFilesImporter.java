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
        final var content = Files.readString(file.toPath());
        if (content.isBlank()) {
            // like an empty YAML file: skipped, instead of failing the whole run
            return null;
        }
        try {
            return objectMapper.readValue(content, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (final JsonProcessingException e) {
            throw new IOException("Invalid JSON in '%s': %s".formatted(file, e.getOriginalMessage()), e);
        }
    }

    @NonNull
    @Override
    protected String writeValueAsString(@NonNull final Map<String, Object> map) throws JsonProcessingException {
        if (map.isEmpty()) {
            return "";
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
    }
}
