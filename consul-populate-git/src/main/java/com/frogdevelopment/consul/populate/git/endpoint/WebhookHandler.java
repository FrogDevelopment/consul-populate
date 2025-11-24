package com.frogdevelopment.consul.populate.git.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.git.GitProperties;

import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

// todo: save informations like last date webhook triggered, to be included in the GitSummary
@Slf4j
@Singleton
@RequiredArgsConstructor
class WebhookHandler {

    private static final GitProperties.Webhook.WebhookHeader GITHUB_WEBHOOK_HEADER = new GitProperties.Webhook.WebhookHeader("X-GitHub-Event", "push", "X-Hub-Signature-256");
    private static final GitProperties.Webhook.WebhookHeader BITBUCKET_WEBHOOK_HEADER = new GitProperties.Webhook.WebhookHeader("X-Event-Key", "repo:push", "X-Hub-Signature");

    private final GitProperties gitProperties;

    HttpResponse<Void> handle(final HttpRequest<?> request) {
        final var webhookHeader = getWebhookHeader();

        final var eventType = request.getHeaders().get(webhookHeader.getHeaderEventKey());
        if (!webhookHeader.getHeaderEventExpectedValue().equals(eventType)) {
            return HttpResponse.notModified();
        }

        final var optionalBody = request.getBody(String.class);
        if (optionalBody.isEmpty()) {
            return HttpResponse.badRequest();
        }
        final var payload = optionalBody.get();

        if (gitProperties.getWebhook().getSecret().isPresent()) {
            final var signature = request.getHeaders().get(webhookHeader.getHeaderSignatureKey());
            if (StringUtils.isEmpty(signature)) {
                return HttpResponse.badRequest();
            }

            final var secret = gitProperties.getWebhook().getSecret().get();
            if (verifySignature(signature, payload, secret)) {
                triggerPullingIfNeeded(request);
            }
        } else {
            triggerPullingIfNeeded(request);
        }

        return HttpResponse.accepted();
    }

    private GitProperties.Webhook.WebhookHeader getWebhookHeader() {
        return switch (gitProperties.getWebhook().getType()) {
            case GITHUB -> GITHUB_WEBHOOK_HEADER;
            case BITBUCKET -> BITBUCKET_WEBHOOK_HEADER;
            case CUSTOM -> {
                final var tmp = gitProperties.getWebhook().getHeader();
                if (tmp == null) {
                    throw new IllegalStateException("[consul.git.webhook.header] is required if CUSTOM type.");
                }
                yield tmp;
            }
        };
    }

    private boolean verifySignature(final String givenSignature, final String payload, final String secret) {
        // todo  check if library exists
        try {
            final var keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            final var mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            final var digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            final var hex = HexFormat.of();
            final var calculatedSignature = "sha256=" + hex.formatHex(digest);
            if (MessageDigest.isEqual(calculatedSignature.getBytes(), givenSignature.getBytes())) {
                log.debug("Signatures match");
                return true;
            } else {
                log.warn("Signatures do not match. Expected signature: {},actual signature: {}", calculatedSignature, givenSignature);
                return false;
            }
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    private void triggerPullingIfNeeded(final HttpRequest<?> request) {
        // GITHUB https://docs.github.com/en/webhooks/webhook-events-and-payloads
        // BITBUCKET https://support.atlassian.com/bitbucket-cloud/docs/event-payloads/#Push
        // todo check that a push was one in the branch configured in GitProperties
    }
}
