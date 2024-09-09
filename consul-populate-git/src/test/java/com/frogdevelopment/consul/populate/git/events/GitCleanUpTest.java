package com.frogdevelopment.consul.populate.git.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.consul.populate.git.RepositoryDirectoryProvider;

import io.micronaut.runtime.server.event.ServerShutdownEvent;

@ExtendWith(MockitoExtension.class)
class GitCleanUpTest {

    @Mock
    private RepositoryDirectoryProvider repositoryDirectoryProvider;
    @Mock
    private ServerShutdownEvent serverShutdownEvent;

    @InjectMocks
    private GitCleanUp gitCleanUp;

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
}
