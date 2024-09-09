package com.frogdevelopment.consul.populate.git.endpoint.handlers.webhook;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import io.micronaut.context.annotation.Requires;

/**
 * GitHub-specific webhook payload handler.
 *
 * <p>Handles GitHub push webhooks with the following characteristics:
 * <ul>
 *   <li>Event header: X-GitHub-Event</li>
 *   <li>Push event value: push</li>
 *   <li>Signature header: X-Hub-Signature-256 (SHA-256)</li>
 *   <li>Branch location: ref field (e.g., "refs/heads/main")</li>
 * </ul>
 *
 * @see <a href="https://docs.github.com/en/webhooks/webhook-events-and-payloads#push">GitHub Push Events</a>
 * @author Le Gall Benoît
 * @since 1.2.0
 */
@Slf4j
@Singleton
@Requires(property = "consul.git.webhook.type", pattern = "github|GITHUB")
public class GithubWebhookPayloadHandler implements WebhookPayloadHandler {

    private static final String EVENT_HEADER_KEY = "X-GitHub-Event";
    private static final String EXPECTED_EVENT_VALUE = "push";
    private static final String SIGNATURE_HEADER_KEY = "X-Hub-Signature-256";

    @Override
    public String getEventHeaderKey() {
        return EVENT_HEADER_KEY;
    }

    @Override
    public String getExpectedEventValue() {
        return EXPECTED_EVENT_VALUE;
    }

    @Override
    public String getSignatureHeaderKey() {
        return SIGNATURE_HEADER_KEY;
    }

    @Override
    public Optional<String> extractBranchName(final JsonNode payload) {
        final var refNode = payload.get("ref");
        if (refNode == null || refNode.isNull()) {
            log.warn("GitHub webhook payload missing 'ref' field");
            return Optional.empty();
        }
        return Optional.of(refNode.asText());
    }
}
