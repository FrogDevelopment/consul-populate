package com.frogdevelopment.consul.populate.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frogdevelopment.consul.populate.files.YamlFilesImporter;

/**
 * The content of a clone is not trusted: anyone with push access could commit a symbolic link to a file of the server.
 */
class GitImporterConfinementTest {

    @TempDir
    Path tempDir;

    @Test
    void should_not_publish_a_file_that_a_link_of_the_clone_takes_from_outside_of_it() throws IOException {
        // given - the clone has a regular file, a link inside itself, and a link to a file of the server
        final var repository = Files.createDirectories(tempDir.resolve("repository"));
        final var outside = Files.createDirectories(tempDir.resolve("server"));
        Files.writeString(repository.resolve("application.yaml"), "from: repository\n");
        Files.writeString(outside.resolve("secret.yaml"), "password: from-the-server\n");
        try {
            Files.createSymbolicLink(repository.resolve("alias.yaml"), Path.of("application.yaml"));
            Files.createSymbolicLink(repository.resolve("stolen.yaml"), Path.of("../server/secret.yaml"));
        } catch (final UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available here: " + e);
        }
        final var directoryProvider = mock(RepositoryDirectoryProvider.class);
        given(directoryProvider.getRepository()).willReturn(repository);

        // when
        final var result = new GitImporter(directoryProvider, new GitProperties(), new ObjectMapper()).execute();

        // then
        assertThat(result).containsOnlyKeys("application", "alias");
        assertThat(result.values()).noneMatch(value -> value.contains("from-the-server"));
    }

    @Test
    void should_confine_the_importer_to_the_clone() {
        // given
        final var repository = Path.of("/tmp/repo");
        final var directoryProvider = mock(RepositoryDirectoryProvider.class);
        given(directoryProvider.getRepository()).willReturn(repository);

        try (var yamlMock = mockConstruction(YamlFilesImporter.class,
                (mock, context) -> given(mock.execute()).willReturn(Map.of("key", "value")))) {
            // when
            new GitImporter(directoryProvider, new GitProperties(), new ObjectMapper()).execute();

            // then
            then(yamlMock.constructed().get(0)).should().confineTo(repository);
        }
    }
}
