package com.frogdevelopment.consul.populate.git.endpoint.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.git.GitProperties;
import com.frogdevelopment.consul.populate.git.pull.GitPullJob;

import io.micronaut.http.HttpResponse;

/**
 * Handler for toggling git scheduled pull at runtime.
 *
 * <p>This handler processes requests to the {@code /git/toggle-poll} endpoint
 * and dynamically starts or stops the {@link GitPullJob} by toggling the
 * current pull state.
 *
 * <h2>Usage</h2>
 * <p>{@code POST /git/toggle-poll} - Toggles pull: if enabled, disables it; if disabled, enables it.
 *
 * <h2>Response Codes</h2>
 * <ul>
 *   <li>{@code 200 OK} - Poll schedule toggled successfully</li>
 * </ul>
 *
 * @author Le Gall Benoît
 * @see GitPullJob
 * @see GitProperties
 * @since 1.2.0
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class TogglePollHandler {

    private final GitProperties gitProperties;
    private final GitPullJob gitPullJob;

    /**
     * Handles a request to toggle git polling on or off.
     *
     * @return {@code 200 OK} after toggling the polling state
     */
    public synchronized HttpResponse<Void> handle() {
        // synchronized: reading the value, flipping it and starting or stopping the job is one step, or two concurrent
        // requests would lose a toggle or leave the job in the opposite state of the property
        final var currentValue = gitProperties.isPollEnabled();
        final var newValue = !currentValue;
        log.info("Toggle Git pull scheduled: {}", newValue);
        gitProperties.setPollEnabled(newValue);
        if (newValue) {
            gitPullJob.start();
        } else {
            gitPullJob.stop();
        }
        return HttpResponse.ok();
    }
}
