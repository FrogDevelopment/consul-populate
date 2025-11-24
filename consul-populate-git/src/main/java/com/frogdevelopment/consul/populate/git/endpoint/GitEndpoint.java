package com.frogdevelopment.consul.populate.git.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Status;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.micronaut.management.endpoint.annotation.Write;

@Slf4j
@Endpoint(value = "git", defaultSensitive = false)
@RequiredArgsConstructor
public class GitEndpoint {

    private final GitSummaryProvider gitSummaryProvider;
    private final PollingHandler pollingHandler;
    private final WebhookHandler webhookHandler;
    private final PullHandler pullHandler;

    @Read
    public GitSummary summary() {
        return gitSummaryProvider.generateSummary();
    }

    @Write
    @Status(HttpStatus.OK)
    public HttpResponse<Void> handleGitActions(@Selector final String action,
                                               final HttpRequest<?> request) {
        return switch (action) {
            case "polling" -> pollingHandler.handle(request);
            case "webhook" -> webhookHandler.handle(request);
            case "pull" -> pullHandler.handle();
            default -> {
                log.warn("Invalid action {}", action);
                yield HttpResponse.notFound();
            }
        };
    }
}
