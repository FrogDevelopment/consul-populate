package com.frogdevelopment.consul.populate.git.endpoint.handlers;

import static com.frogdevelopment.consul.populate.git.pull.Trigger.FORCED;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.git.pull.GitPull;

import io.micronaut.http.HttpResponse;

/**
 * Handler for manual pull requests via the management endpoint.
 *
 * <p>Triggers a forced git pull operation when invoked through
 * the {@code /git/force-pull} endpoint. This allows administrators to manually
 * synchronize the local repository with the remote without waiting for
 * scheduled polling or webhook events.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 * @see GitPull
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class ForcePullHandler {

    private final GitPull gitPull;

    /**
     * Handles a manual pull request by triggering a forced git pull.
     *
     * @return {@link HttpResponse#ok()} after the pull operation completes
     */
    public HttpResponse<Void> handle() {
        gitPull.pull(FORCED);
        return HttpResponse.ok();
    }
}
