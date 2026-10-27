package com.frogdevelopment.consul.populate.git.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;

import jakarta.inject.Singleton;

import org.apache.commons.io.FileUtils;

import com.frogdevelopment.consul.populate.git.RepositoryDirectoryProvider;
import com.frogdevelopment.consul.populate.git.pull.GitPull;
import com.frogdevelopment.consul.populate.git.pull.GitPullJob;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerShutdownEvent;

/**
 * Optional cleanup handler that deletes the cloned git repository on server shutdown.
 * Only active when {@code consul.git.clean-up=true} is configured.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
@Requires(property = "consul.git.clean-up", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class GitCleanUp {

    /** Provides the directory that holds the cloned git repository */
    private final RepositoryDirectoryProvider repositoryDirectoryProvider;
    private final GitPullJob gitPullJob;
    private final GitPull gitPull;

    /**
     * Deletes the temporary directory of the cloned git repository on server shutdown.
     *
     * <p>The pull job is stopped first and the deletion waits for the pull that may be running (and the population it
     * leads to), so the clone is never deleted under a running pull.
     *
     * @param ignored the server shutdown event
     */
    @Blocking
    @EventListener
    public void onServerShutdownEvent(final ServerShutdownEvent ignored) {
        gitPullJob.stop();
        // empty when the repository was never requested: there is nothing to delete, and nothing is created to be deleted
        repositoryDirectoryProvider.getWorkingDirectory()
                .ifPresent(directory -> gitPull.runExclusively(() -> delete(directory)));
    }

    private static void delete(final Path directory) {
        try {
            FileUtils.deleteDirectory(directory.toFile());
            log.info("Local Git Repository {} deleted", directory);
        } catch (final IOException e) {
            log.error("Failed to delete repository", e);
        }
    }
}
