package com.frogdevelopment.consul.populate.files;

import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;

import com.frogdevelopment.consul.populate.DataImporter;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArrayUtils;

@RequiredArgsConstructor
abstract sealed class FilesImporter implements DataImporter
        permits JsonFilesImporter, PropertiesFilesImporter, YamlFilesImporter {

    private final ConsulFileProperties fileProperties;

    @NonNull
    @Override
    public Map<String, String> execute() {
        // validate paths
        final var rootPath = Paths.get(fileProperties.getRootPath());
        if (!rootPath.toFile().exists()) {
            throw new IllegalArgumentException("Root directory does not exist: " + rootPath);
        }
        final var targetPath = rootPath.resolve(fileProperties.getTarget());
        if (!targetPath.toFile().exists()) {
            throw new IllegalArgumentException("Target directory does not exist: " + targetPath);
        }

        // list files in root directory
        var rootFiles = rootPath.toFile().listFiles(this::filterFile);
        if (ArrayUtils.isEmpty(rootFiles)) {
            throw new IllegalArgumentException("No configuration files found in root directory: " + rootPath);
        }

        // list files in target subdirectory
        var targetFiles = targetPath.toFile().listFiles(this::filterFile);
        if (ArrayUtils.isEmpty(targetFiles)) {
            throw new IllegalArgumentException("No configuration files found in target directory: " + targetPath);
        }

        try {
            // fore each target, merge in root if exists
            var rootFilesMap = new HashMap<String, Map<String, Object>>();
            for (var rootFile : rootFiles) {
                rootFilesMap.put(rootFile.getName(), readFile(rootFile));
            }

            for (var targetFile : targetFiles) {
                var target = readFile(targetFile);
                var root = rootFilesMap.get(targetFile.getName());
                if (root != null) {
                    mergeMaps(root, target);
                } else {
                    rootFilesMap.put(targetFile.getName(), target);
                }
            }

            var result = new HashMap<String, String>();
            for (final var entry : rootFilesMap.entrySet()) {
                var name = FilenameUtils.removeExtension(entry.getKey());
                result.put(name, writeValueAsString(entry.getValue()));
            }

            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to process the configurations to import. Please check the error logs", e);
        }
    }

    private boolean filterFile(@NonNull final File file) {
        if (file.isDirectory()) {
            return false;
        }
        final var extension = FilenameUtils.getExtension(file.getName());
        return isExtensionAccepted(extension);
    }

    protected abstract boolean isExtensionAccepted(@NonNull final String extension);

    @NonNull
    protected abstract Map<String, Object> readFile(@NonNull final File file) throws IOException;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mergeMaps(@NonNull final Map<String, Object> root, final Map<String, Object> target) {
        target.forEach((key, value) -> root.merge(key, value, (rootKid, targetKid) -> {
            if (rootKid instanceof Map matRootKid) {
                mergeMaps(matRootKid, (Map) targetKid);
                return rootKid;
            } else {
                return rootKid.getClass().cast(targetKid);
            }
        }));
    }

    @NonNull
    protected abstract String writeValueAsString(@NonNull final Map<String, Object> map) throws IOException;
}
