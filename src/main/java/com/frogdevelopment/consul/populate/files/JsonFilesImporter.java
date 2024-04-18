package com.frogdevelopment.consul.populate.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;

@Singleton
@Requires(property = "consul.files.format", value = "JSON")
public final class JsonFilesImporter extends FilesImporter {

    private static final List<String> EXTENSIONS = List.of("json");

    private final ObjectMapper objectMapper;

    public JsonFilesImporter(final ConsulFileProperties fileProperties, ObjectMapper objectMapper) {
        super(fileProperties);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean isExtensionAccepted(@NonNull final String extension) {
        return EXTENSIONS.contains(extension.toLowerCase());
    }

    @NonNull
    @Override
    protected Map<String, Object> readFile(@NonNull final File file) throws IOException {
        try (var reader = Files.newBufferedReader(file.toPath())) {
            final var typeRef
                    = new TypeReference<LinkedHashMap<String, Object>>() {
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
