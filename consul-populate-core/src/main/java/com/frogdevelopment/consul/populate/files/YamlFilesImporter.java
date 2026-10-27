package com.frogdevelopment.consul.populate.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

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
            final Object loaded = newYaml().load(reader);
            if (loaded == null) {
                return null;
            }
            if (!(loaded instanceof SequencedMap<?, ?> mapping)) {
                throw new IOException("The top level of '%s' must be a mapping, not a %s"
                        .formatted(file, loaded.getClass().getSimpleName()));
            }
            @SuppressWarnings("unchecked")
            final var result = (SequencedMap<String, Object>) mapping;
            return result;
        } catch (final YAMLException e) {
            // SnakeYAML reports the position but not the file
            throw new IOException("Invalid YAML in '%s': %s".formatted(file, e.getMessage()), e);
        }
    }

    @NonNull
    @Override
    protected String writeValueAsString(@NonNull final Map<String, Object> map) {
        if (map.isEmpty()) {
            return "";
        }
        return newYaml().dumpAsMap(map);
    }

    /**
     * {@link Yaml} is not thread-safe, and this importer is a singleton that can run concurrently (poll, webhook,
     * force-pull). Creating an instance per use is cheap for a handful of files and, unlike a {@link ThreadLocal},
     * leaves nothing to clean up on long-lived threads.
     */
    private static Yaml newYaml() {
        return new Yaml();
    }
}
