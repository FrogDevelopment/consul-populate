package com.frogdevelopment.consul.populate.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

import org.yaml.snakeyaml.Yaml;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * Implementation for YAML files import
 *
 * @author Le Gall Benoît
 * @see Yaml
 * @since 1.0.0
 */
public final class YamlFilesImporter extends FilesImporter {

    private static final List<String> EXTENSIONS = List.of("yaml", "yml");

    private final Yaml yaml = new Yaml();

    /**
     * Constructor
     *
     * @param rootPath   Path to root directory
     * @param targetPath Subdirectory used to override root configurations
     **/
    public YamlFilesImporter(final Path rootPath, final Path targetPath) {
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
            return yaml.load(reader);
        }
    }

    @NonNull
    @Override
    protected String writeValueAsString(@NonNull final Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return yaml.dumpAsMap(map);
    }
}
