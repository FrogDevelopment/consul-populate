package com.frogdevelopment.consul.populate.git.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.consul.populate.git.RepositoryDirectoryProvider;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.runtime.server.event.ServerShutdownEvent;

@ExtendWith(MockitoExtension.class)
class GitCleanUpTest {

    @Mock
    private RepositoryDirectoryProvider repositoryDirectoryProvider;
    @Mock
    private ServerShutdownEvent serverShutdownEvent;

    @InjectMocks
    private GitCleanUp gitCleanUp;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUpLogger() {
        logger = (Logger) org.slf4j.LoggerFactory.getLogger(GitCleanUp.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDownLogger() {
        logger.detachAppender(logAppender);
    }

    @Test
    void onServerShutdownEvent_shouldDeleteRepositoryDirectory(@TempDir Path tempDir) throws IOException {
        // given
        var repoDir = tempDir.resolve("repo");
        Files.createDirectories(repoDir);
        Files.createFile(repoDir.resolve("test-file.txt"));
        assertThat(repoDir).exists();

        given(repositoryDirectoryProvider.getRepository()).willReturn(repoDir);

        // when
        gitCleanUp.onServerShutdownEvent(serverShutdownEvent);

        // then
        assertThat(repoDir).doesNotExist();
    }

    @Test
    void onServerShutdownEvent_shouldDeleteNestedDirectories(@TempDir Path tempDir) throws IOException {
        // given
        var repoDir = tempDir.resolve("repo");
        var nestedDir = repoDir.resolve("nested/deep/directory");
        Files.createDirectories(nestedDir);
        Files.createFile(nestedDir.resolve("nested-file.txt"));
        assertThat(repoDir).exists();

        given(repositoryDirectoryProvider.getRepository()).willReturn(repoDir);

        // when
        gitCleanUp.onServerShutdownEvent(serverShutdownEvent);

        // then
        assertThat(repoDir).doesNotExist();
    }

    @Test
    void onServerShutdownEvent_shouldHandleNonExistentDirectory(@TempDir Path tempDir) {
        // given
        var nonExistentDir = tempDir.resolve("non-existent");
        assertThat(nonExistentDir).doesNotExist();

        given(repositoryDirectoryProvider.getRepository()).willReturn(nonExistentDir);

        // when - should not throw
        gitCleanUp.onServerShutdownEvent(serverShutdownEvent);

        // then - no exception thrown
        assertThat(nonExistentDir).doesNotExist();
    }

    @Test
    void onServerShutdownEvent_shouldHandleIOExceptionSilently(@TempDir Path tempDir) {
        // given
        var repoDir = tempDir.resolve("repo");

        given(repositoryDirectoryProvider.getRepository()).willReturn(repoDir);

        try (var stub = Mockito.mockStatic(FileUtils.class)) {
            stub.when(() -> FileUtils.deleteDirectory(repoDir.toFile()))
                    .thenThrow(new IOException("Simulated IO error"));

            // when - should not throw
            gitCleanUp.onServerShutdownEvent(serverShutdownEvent);

            // then - error is logged
            assertThat(logAppender.list)
                    .hasSize(1)
                    .first()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).hasToString("ERROR");
                        assertThat(event.getMessage()).isEqualTo("Failed to delete repository");
                        assertThat(event.getThrowableProxy()).isNotNull();
                        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("Simulated IO error");
                    });
        }
    }
}
