package com.frogdevelopment.consul.populate.git.endpoint.handlers.webhook;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Strategy interface for handling provider-specific webhook payloads.
 *
 * <p>Implementations handle the differences between Git providers (GitHub, Bitbucket, etc.)
 * including header names, expected event values, and payload structure for branch extraction.
 *
 * <p>Built-in implementations are provided for GitHub and Bitbucket. For other providers,
 * users can implement this interface and register their bean with {@code @Singleton}.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
public interface WebhookPayloadHandler {

    /**
     * Returns the HTTP header key used to identify the event type.
     *
     * @return the event type header key (e.g., "X-GitHub-Event", "X-Event-Key")
     */
    String getEventHeaderKey();

    /**
     * Returns the expected value for push events.
     *
     * @return the expected event value (e.g., "push", "repo:push")
     */
    String getExpectedEventValue();

    /**
     * Returns the HTTP header key used for the webhook signature.
     *
     * @return the signature header key (e.g., "X-Hub-Signature-256", "X-Hub-Signature")
     */
    String getSignatureHeaderKey();

    /**
     * Extracts the branch name from the webhook payload.
     *
     * <p>The branch name may include the "refs/heads/" prefix depending on the provider.
     * The caller is responsible for normalizing the branch name if needed.
     *
     * @param payload the JSON payload from the webhook request
     * @return the branch name if it can be extracted, or empty if not found
     */
    Optional<String> extractBranchName(JsonNode payload);
}
