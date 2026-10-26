package com.frogdevelopment.consul.populate.git.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.git.GitProperties;
import com.frogdevelopment.consul.populate.git.pull.GitPull;
import com.frogdevelopment.consul.populate.git.pull.GitPullJob;

import io.micronaut.core.annotation.Blocking;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.scheduling.annotation.Async;

/**
 * Listener for Micronaut server lifecycle events to manage Git-based configuration synchronization.
 *
 * <p>On server startup, this listener:
 * <ul>
 *   <li>Populates Consul with configuration from the cloned Git repository</li>
 *   <li>Starts the scheduled pull job if polling is enabled</li>
 * </ul>
 *
 * <p>On server shutdown, this listener stops the scheduled pull job if it was running.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 * @see GitPullJob
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class ServerEventsListener {

    private final GitPull gitPull;
    private final GitProperties gitProperties;
    private final GitPullJob gitPullJob;

    /**
     * Handles server startup by populating Consul and optionally starting the pull job.
     *
     * <p>This method runs asynchronously to avoid blocking server startup. A failed initial population is logged and
     * does not prevent the pull job from starting: the next pull retries it.
     *
     * @param ignored the server startup event (unused)
     */
    @Async
    @EventListener
    public void onServerStartupEvent(final ServerStartupEvent ignored) {
        log.info("Populating Consul with repository");
        try {
            gitPull.populate();
        } catch (final Exception e) {
            log.error("Initial population of Consul failed, it will be retried on the next pull", e);
        }

        if (gitProperties.isPollEnabled()) {
            gitPullJob.start();
        }
    }

    /**
     * Handles server shutdown by stopping the pull job if it is currently running.
     *
     * @param ignored the server shutdown event (unused)
     */
    @Blocking
    @EventListener
    public void onServerShutdownEvent(final ServerShutdownEvent ignored) {
        if (gitPullJob.isRunning()) {
            gitPullJob.stop();
        }
    }
}
