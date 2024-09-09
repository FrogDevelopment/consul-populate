package com.frogdevelopment.consul.populate.git.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.frogdevelopment.consul.populate.git.endpoint.handlers.ForcePullHandler;
import com.frogdevelopment.consul.populate.git.endpoint.handlers.TogglePollHandler;
import com.frogdevelopment.consul.populate.git.endpoint.handlers.WebhookHandler;
import com.frogdevelopment.consul.populate.git.endpoint.model.GitSummary;
import com.frogdevelopment.consul.populate.git.endpoint.model.GitSummaryProvider;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Status;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.micronaut.management.endpoint.annotation.Write;

/**
 * Micronaut management endpoint for Git-based configuration operations.
 *
 * <p>This endpoint exposes Git repository management functionality at {@code /git}.
 * It provides both read and write operations for monitoring and controlling
 * the Git-based configuration synchronization.
 *
 * <h2>Read Operations (GET /git)</h2>
 * <p>Returns a {@link GitSummary} containing the current state of the Git repository,
 * including repository info, HEAD commit details, and pull status.
 *
 * <h2>Write Operations (POST /git/{action})</h2>
 * <p>Supports the following actions:
 * <ul>
 *   <li>{@code toggle-poll} - Toggle pull on/off</li>
 *   <li>{@code webhook} - Handle incoming webhook events from git providers</li>
 *   <li>{@code force-pull} - Force an immediate git pull operation</li>
 * </ul>
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 * @see GitSummary
 */
@Slf4j
@Endpoint(value = "git", defaultSensitive = false)
@RequiredArgsConstructor
public class GitEndpoint {

    static final String ACTION_FORCE_PULL = "force-pull";
    static final String ACTION_TOGGLE_POLL = "toggle-poll";
    static final String ACTION_WEBHOOK = "webhook";

    private final GitSummaryProvider gitSummaryProvider;
    private final TogglePollHandler togglePollHandler;
    private final WebhookHandler webhookHandler;
    private final ForcePullHandler forcePullHandler;

    /**
     * Returns a summary of the current Git repository state.
     *
     * @return the Git summary containing repository, pull, and file configuration details
     */
    @Read
    public GitSummary summary() {
        return gitSummaryProvider.generateSummary();
    }

    /**
     * Handles write actions on the Git endpoint.
     *
     * @param action  the action to perform: "force-pull", "toggle-poll", or "webhook"
     * @param request the HTTP request (used by webhook handler for headers)
     * @param body    the request body (used by webhook handler, may be null)
     * @return the HTTP response based on the action result
     */
    @Write
    @Status(HttpStatus.OK)
    public HttpResponse<Void> handleGitActions(@Selector final String action,
                                               final HttpRequest<?> request,
                                               @Body @Nullable final String body) {
        return switch (action) {
            case ACTION_FORCE_PULL -> forcePullHandler.handle();
            case ACTION_TOGGLE_POLL -> togglePollHandler.handle();
            case ACTION_WEBHOOK -> webhookHandler.handle(request, body);
            default -> {
                log.warn("Invalid action {}", action);
                yield HttpResponse.notFound();
            }
        };
    }
}
