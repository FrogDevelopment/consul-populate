package com.frogdevelopment.consul.populate.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import jakarta.inject.Singleton;

import org.yaml.snakeyaml.Yaml;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

@Singleton
@Requires(property = "consul.files.format", value = "YAML")
public final class YamlFilesImporter extends FilesImporter {

    private static final List<String> EXTENSIONS = List.of("yaml", "yml");

    private final Yaml yaml = new Yaml();

    public YamlFilesImporter(final ImportFileProperties importProperties) {
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
            return yaml.load(reader);
        }
    }

    @NonNull
    @Override
    protected String writeValueAsString(@NonNull final Map<String, Object> map) {
        return yaml.dumpAsMap(map);
    }
}
