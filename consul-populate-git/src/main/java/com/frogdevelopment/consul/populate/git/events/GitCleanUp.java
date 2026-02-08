package com.frogdevelopment.consul.populate.git.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import jakarta.inject.Singleton;

import org.apache.commons.io.FileUtils;

import com.frogdevelopment.consul.populate.git.RepositoryDirectoryProvider;

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
@Requires(property = "consul.git.clean-up", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
public class GitCleanUp {

    /** Provides the cloned git repository directory path */
    private final RepositoryDirectoryProvider repositoryDirectoryProvider;

    /**
     * Deletes the cloned git repository directory on server shutdown.
     *
     * @param ignored the server shutdown event
     */
    @Blocking
    @EventListener
    public void onServerShutdownEvent(final ServerShutdownEvent ignored) {
        try {
            final var repositoryDirectory = repositoryDirectoryProvider.getRepository();
            FileUtils.deleteDirectory(repositoryDirectory.toFile());
            log.info("Local Git Repository {} deleted", repositoryDirectory);
        } catch (final IOException e) {
            log.error("Failed to delete repository", e);
        }
    }
}
