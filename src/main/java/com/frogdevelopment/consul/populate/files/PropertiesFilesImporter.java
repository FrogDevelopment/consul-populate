package com.frogdevelopment.consul.populate.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;

@Singleton
@Requires(property = "consul.files.format", value = "PROPERTIES")
public final class PropertiesFilesImporter extends FilesImporter {

    private static final List<String> EXTENSIONS = List.of("properties");

    public PropertiesFilesImporter(final ConsulFileProperties fileProperties) {
        super(fileProperties);
    }

    @Override
    protected boolean isExtensionAccepted(@NonNull final String extension) {
        return EXTENSIONS.contains(extension);
    }

    @NonNull
    @Override
    protected Map<String, Object> readFile(@NonNull final File file) throws IOException {
        try (var reader = Files.newBufferedReader(file.toPath())) {
            final var properties = new Properties();
            properties.load(reader);
            return properties.entrySet()
                    .stream()
                    .collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue));
        }
    }

    @NonNull
    @Override
    protected String writeValueAsString(@NonNull final Map<String, Object> map) {
        return map.entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }
}
