package com.frogdevelopment.consul.populate.git.endpoint.handlers.webhook;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import io.micronaut.context.annotation.Requires;

/**
 * Bitbucket-specific webhook payload handler.
 *
 * <p>Handles Bitbucket push webhooks with the following characteristics:
 * <ul>
 *   <li>Event header: X-Event-Key</li>
 *   <li>Push event value: repo:push</li>
 *   <li>Signature header: X-Hub-Signature (SHA-256)</li>
 *   <li>Branch location: push.changes[].new.name</li>
 * </ul>
 *
 * @see <a href="https://support.atlassian.com/bitbucket-cloud/docs/event-payloads/#Push">Bitbucket Push Events</a>
 * @author Le Gall Benoît
 * @since 1.2.0
 */
@Slf4j
@Singleton
@Requires(property = "consul.git.webhook.type", pattern = "bitbucket|BITBUCKET")
public class BitbucketWebhookPayloadHandler implements WebhookPayloadHandler {

    private static final String EVENT_HEADER_KEY = "X-Event-Key";
    private static final String EXPECTED_EVENT_VALUE = "repo:push";
    private static final String SIGNATURE_HEADER_KEY = "X-Hub-Signature";

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

    /**
     * @return the first branch of the push only: use {@link #extractBranchNames(JsonNode)} to know every branch a
     * push updated
     */
    @Override
    public Optional<String> extractBranchName(final JsonNode payload) {
        return branchNames(payload).stream().findFirst();
    }

    /**
     * A single push can update several branches, each one being a {@code push.changes[]} entry.
     */
    @Override
    public Set<String> extractBranchNames(final JsonNode payload) {
        return branchNames(payload);
    }

    private static Set<String> branchNames(final JsonNode payload) {
        final var pushNode = payload.get("push");
        if (pushNode == null || pushNode.isNull()) {
            log.warn("Bitbucket webhook payload missing 'push' field");
            return Set.of();
        }

        final var changesNode = pushNode.get("changes");
        if (changesNode == null || !changesNode.isArray()) {
            log.warn("Bitbucket webhook payload missing 'push.changes' array");
            return Set.of();
        }

        final var names = new LinkedHashSet<String>();
        for (final var change : changesNode) {
            final var newNode = change.get("new");
            if (newNode != null && !newNode.isNull()) {
                final var nameNode = newNode.get("name");
                if (nameNode != null && !nameNode.isNull()) {
                    names.add(nameNode.asText());
                }
            }
        }

        if (names.isEmpty()) {
            log.debug("No new branch found in Bitbucket push changes");
        }
        return names;
    }
}
