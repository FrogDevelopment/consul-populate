package com.frogdevelopment.consul.populate.git.endpoint.handlers;

import static com.frogdevelopment.consul.populate.git.pull.Trigger.WEBHOOK;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frogdevelopment.consul.populate.git.GitProperties;
import com.frogdevelopment.consul.populate.git.endpoint.handlers.webhook.WebhookPayloadHandler;
import com.frogdevelopment.consul.populate.git.pull.GitPull;

import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;

/**
 * Handler for processing webhook events from git providers (GitHub, Bitbucket, or custom).
 *
 * <p>This handler is invoked via the {@code /git/webhook} endpoint and performs the following:
 * <ol>
 *   <li>Validates the event type header matches the expected push event</li>
 *   <li>Optionally verifies the webhook signature (HMAC SHA-1 or SHA-256) if a secret is configured</li>
 *   <li>Extracts the branch name from the payload using the configured {@link WebhookPayloadHandler}</li>
 *   <li>Triggers a git pull if the branch matches the configured branch</li>
 * </ol>
 *
 * <p>The specific header names and payload parsing are delegated to a {@link WebhookPayloadHandler}
 * implementation, allowing support for different git providers.
 *
 * <h2>Response Codes</h2>
 * <ul>
 *   <li>{@code 202 Accepted} - Webhook processed and pull triggered</li>
 *   <li>{@code 304 Not Modified} - Webhook ignored (wrong event type, branch mismatch, or webhook not configured)</li>
 *   <li>{@code 400 Bad Request} - Missing or invalid payload</li>
 *   <li>{@code 401 Unauthorized} - Signature verification failed or missing when secret is configured</li>
 *   <li>{@code 501 Not Implemented} - No {@link WebhookPayloadHandler} available for the configured type</li>
 * </ul>
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 * @see WebhookPayloadHandler
 * @see GitPull
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class WebhookHandler {

    private static final Pattern REFS_HEADS_PATTERN = Pattern.compile("^refs/heads/");

    private final GitProperties gitProperties;
    private final ObjectMapper objectMapper;
    private final GitPull gitPull;
    private final Optional<WebhookPayloadHandler> payloadHandler;

    /**
     * Handles an incoming webhook request from a git provider.
     *
     * @param request    the HTTP request containing headers for event type and signature
     * @param stringBody the raw JSON payload from the webhook
     * @return an appropriate HTTP response based on the processing result
     */
    public HttpResponse<Void> handle(final HttpRequest<?> request, final String stringBody) {
        if (gitProperties.getWebhook() == null) {
            log.warn("No webhook configured");
            return HttpResponse.notModified();
        }

        if (payloadHandler.isEmpty()) {
            log.warn("No WebhookPayloadHandler configured for webhook type: {}",
                    gitProperties.getWebhook().getType());
            return HttpResponse.status(HttpStatus.NOT_IMPLEMENTED);
        }

        final var handler = payloadHandler.get();

        final var eventType = request.getHeaders().get(handler.getEventHeaderKey());
        if (!handler.getExpectedEventValue().equals(eventType)) {
            return HttpResponse.notModified();
        }

        if (stringBody == null) {
            return HttpResponse.badRequest();
        }

        final var secret = gitProperties.getWebhook().getSecret();
        if (StringUtils.isNotEmpty(secret)) {
            final var signature = request.getHeaders().get(handler.getSignatureHeaderKey());
            if (StringUtils.isEmpty(signature)) {
                log.warn("Webhook secret is configured but no signature header was provided");
                return HttpResponse.unauthorized();
            }
            if (!verifySignature(signature, stringBody, secret)) {
                return HttpResponse.unauthorized();
            }
        }

        return triggerPullingIfNeeded(stringBody, handler);
    }

    private boolean verifySignature(final String givenSignature, final String payload, final String secret) {
        final String algorithm;
        final String prefix;

        if (givenSignature.startsWith("sha256=")) {
            algorithm = "HmacSHA256";
            prefix = "sha256=";
        } else if (givenSignature.startsWith("sha1=")) {
            algorithm = "HmacSHA1";
            prefix = "sha1=";
        } else {
            log.warn("Unknown signature format: {}", givenSignature.substring(0, Math.min(10, givenSignature.length())));
            return false;
        }

        try {
            final var keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm);
            final var mac = Mac.getInstance(algorithm);
            mac.init(keySpec);
            final var digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            final var calculatedSignature = prefix + HexFormat.of().formatHex(digest);

            if (MessageDigest.isEqual(calculatedSignature.getBytes(StandardCharsets.UTF_8),
                    givenSignature.getBytes(StandardCharsets.UTF_8))) {
                log.debug("Signature verified successfully");
                return true;
            } else {
                log.warn("Signature mismatch - expected: {}, received: {}", calculatedSignature, givenSignature);
                return false;
            }
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to verify signature: {}", e.getMessage(), e);
            return false;
        }
    }

    private HttpResponse<Void> triggerPullingIfNeeded(final String body, final WebhookPayloadHandler handler) {
        try {
            final var jsonNode = objectMapper.readTree(body);
            final var branchName = handler.extractBranchName(jsonNode);

            final var toPull = branchName
                    .map(ref -> REFS_HEADS_PATTERN.matcher(ref).replaceFirst(""))
                    .map(branch -> branch.equals(gitProperties.getBranch()))
                    .orElseGet(() -> {
                        log.warn("Could not extract branch from webhook payload, triggering pull anyway");
                        return true;
                    });

            if (toPull) {
                gitPull.pull(WEBHOOK);
                log.info("Webhook triggered pull for branch: {}", gitProperties.getBranch());
                return HttpResponse.accepted();
            } else {
                log.debug("Webhook ignored - branch mismatch (received: {}, expected: {})",
                        branchName.orElse("unknown"), gitProperties.getBranch());
                return HttpResponse.notModified();
            }
        } catch (final JsonProcessingException e) {
            log.warn("Failed to parse webhook payload: {}", e.getMessage(), e);
            return HttpResponse.badRequest();
        }
    }
}
