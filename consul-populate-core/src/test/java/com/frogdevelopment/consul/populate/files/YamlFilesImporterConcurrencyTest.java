package com.frogdevelopment.consul.populate.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The importer is a singleton and populations can overlap (poll, webhook, force-pull), while SnakeYAML's {@code Yaml}
 * is not thread-safe: concurrent use must neither fail nor mix the content of different files.
 */
class YamlFilesImporterConcurrencyTest {

    private static final int THREADS = 16;
    private static final int ITERATIONS = 300;

    @TempDir
    Path tempDir;

    @Test
    void should_read_and_write_concurrently_without_mixing_up_the_content() throws Exception {
        // given
        final var importer = new YamlFilesImporter(tempDir, tempDir);
        final var start = new CountDownLatch(1);
        final var tasks = new ArrayList<Callable<Void>>();
        for (var thread = 0; thread < THREADS; thread++) {
            final var id = thread;
            final var file = Files.writeString(tempDir.resolve("file-" + id + ".yaml"),
                    "id: " + id + "\nitems:\n  - a" + id + "\n  - b" + id + "\nnested:\n  key: value-" + id + "\n");
            final Map<String, Object> expected = Map.of(
                    "id", id,
                    "items", List.of("a" + id, "b" + id),
                    "nested", Map.of("key", "value-" + id));

            tasks.add(() -> {
                start.await();
                for (var i = 0; i < ITERATIONS; i++) {
                    assertThat(importer.readFile(file.toFile())).isEqualTo(expected);
                    assertThat(importer.writeValueAsString(expected))
                            .contains("id: " + id)
                            .contains("value-" + id);
                }
                return null;
            });
        }

        // when
        try (final var executor = Executors.newFixedThreadPool(THREADS)) {
            final var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();

            // then - get() rethrows the first failure of any thread
            for (final var future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        }
    }
}
