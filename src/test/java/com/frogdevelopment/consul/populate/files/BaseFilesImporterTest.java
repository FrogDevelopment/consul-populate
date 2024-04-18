package com.frogdevelopment.consul.populate.files;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.TestInstance;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;

@MicronautTest(startApplication = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseFilesImporterTest implements TestPropertyProvider {

    @Override
    public @NonNull Map<String, String> getProperties() {
        final var loader = this.getClass().getClassLoader();
        final var rootPath = Optional.ofNullable(loader.getResource("files/application.json"))
                .map(url -> Path.of(url.getPath()).getParent().toString())
                .orElseThrow();
        return Map.of(
                "consul.type", "FILES",
                "consul.files.target", "test",
                "consul.files.rootPath", rootPath);
    }
}
