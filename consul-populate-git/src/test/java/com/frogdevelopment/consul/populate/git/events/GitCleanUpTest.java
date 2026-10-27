package com.frogdevelopment.consul.populate.git.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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
import com.frogdevelopment.consul.populate.git.pull.GitPull;
import com.frogdevelopment.consul.populate.git.pull.GitPullJob;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.runtime.server.event.ServerShutdownEvent;

@ExtendWith(MockitoExtension.class)
class GitCleanUpTest {

    @Mock
    private RepositoryDirectoryProvider repositoryDirectoryProvider;
    @Mock
    private GitPullJob gitPullJob;
    @Mock
    private GitPull gitPull;
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

    /** Runs what is given to {@code runExclusively}, as the real {@link GitPull} does once it holds its lock. */
    private void runExclusivelyRunsTheAction() {
        willAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).given(gitPull).runExclusively(any());
    }

    @Test
    void onServerShutdownEvent_shouldDeleteTheWholeWorkingDirectory(@TempDir Path tempDir) throws IOException {
        // given - consul-populate-XXXX/<repo>/..., not only the clone: an empty temporary directory must not stay behind
        var workingDirectory = tempDir.resolve("consul-populate-1234");
        var repoDir = workingDirectory.resolve("repo");
        Files.createDirectories(repoDir);
        Files.createFile(repoDir.resolve("test-file.txt"));
        given(repositoryDirectoryProvider.getWorkingDirectory()).willReturn(Optional.of(workingDirectory));
        runExclusivelyRunsTheAction();

        // when
        gitCleanUp.onServerShutdownEvent(serverShutdownEvent);

        // then
        assertThat(workingDirectory).doesNotExist();
        assertThat(tempDir).exists();
    }

    @Test
    void onServerShutdownEvent_shouldDeleteNestedDirectories(@TempDir Path tempDir) throws IOException {
        // given
        var workingDirectory = tempDir.resolve("consul-populate-1234");
        var nestedDir = workingDirectory.resolve("repo/nested/deep/directory");
        Files.createDirectories(nestedDir);
        Files.createFile(nestedDir.resolve("nested-file.txt"));
        given(repositoryDirectoryProvider.getWorkingDirectory()).willReturn(Optional.of(workingDirectory));
        runExclusivelyRunsTheAction();

        // when
        gitCleanUp.onServerShutdownEvent(serverShutdownEvent);

        // then
        assertThat(workingDirectory).doesNotExist();
    }

    @Test
    void onServerShutdownEvent_shouldStopThePollingThenDeleteWhileNoPullIsRunning(@TempDir Path tempDir) throws IOException {
        // given
        var workingDirectory = Files.createDirectories(tempDir.resolve("consul-populate-1234"));
        given(repositoryDirectoryProvider.getWorkingDirectory()).willReturn(Optional.of(workingDirectory));
        runExclusivelyRunsTheAction();

        // when
        gitCleanUp.onServerShutdownEvent(serverShutdownEvent);

        // then - no new scheduled pull, and the deletion goes through the lock of the pulls
        var inOrder = inOrder(gitPullJob, gitPull);
        inOrder.verify(gitPullJob).stop();
        inOrder.verify(gitPull).runExclusively(any());
    }

    @Test
    void onServerShutdownEvent_shouldDoNothing_whenTheRepositoryWasNeverCreated() {
        // given
        given(repositoryDirectoryProvider.getWorkingDirectory()).willReturn(Optional.empty());

        // when
        gitCleanUp.onServerShutdownEvent(serverShutdownEvent);

        // then - and it does not create a directory just to delete it
        then(gitPull).should(never()).runExclusively(any());
        then(repositoryDirectoryProvider).should(never()).getRepository();
    }

    @Test
    void onServerShutdownEvent_shouldHandleNonExistentDirectory(@TempDir Path tempDir) {
        // given
        var nonExistentDir = tempDir.resolve("non-existent");
        given(repositoryDirectoryProvider.getWorkingDirectory()).willReturn(Optional.of(nonExistentDir));
        runExclusivelyRunsTheAction();

        // when - should not throw
        gitCleanUp.onServerShutdownEvent(serverShutdownEvent);

        // then
        assertThat(nonExistentDir).doesNotExist();
    }

    @Test
    void onServerShutdownEvent_shouldHandleIOExceptionSilently(@TempDir Path tempDir) {
        // given
        var workingDirectory = tempDir.resolve("consul-populate-1234");
        given(repositoryDirectoryProvider.getWorkingDirectory()).willReturn(Optional.of(workingDirectory));
        runExclusivelyRunsTheAction();

        try (var stub = Mockito.mockStatic(FileUtils.class)) {
            stub.when(() -> FileUtils.deleteDirectory(workingDirectory.toFile()))
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
                    });
        }
    }
}
