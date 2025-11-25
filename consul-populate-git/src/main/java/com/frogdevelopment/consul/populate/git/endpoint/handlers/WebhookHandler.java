package com.frogdevelopment.consul.populate.git.endpoint.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.frogdevelopment.consul.populate.git.pull.GitPull;
import io.micronaut.core.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.git.GitProperties;
import com.frogdevelopment.consul.populate.git.GitProperties.Webhook.WebhookHeader;

import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

import static com.frogdevelopment.consul.populate.git.pull.Origin.FORCED;
import static com.frogdevelopment.consul.populate.git.pull.Origin.WEBHOOK;

// todo: save informations like last date webhook triggered, to be included in the GitSummary
@Slf4j
@Singleton
@RequiredArgsConstructor
public class WebhookHandler {

    private static final WebhookHeader GITHUB_WEBHOOK_HEADER = new WebhookHeader("X-GitHub-Event", "push", "X-Hub-Signature-256");
    private static final WebhookHeader BITBUCKET_WEBHOOK_HEADER = new WebhookHeader("X-Event-Key", "repo:push", "X-Hub-Signature");

    private final GitProperties gitProperties;
    private final ObjectMapper objectMapper;
    private final GitPull gitPull;

    public HttpResponse<Void> handle(final HttpRequest<?> request, String body) {
        final var webhookHeader = getWebhookHeader();

        final var eventType = request.getHeaders().get(webhookHeader.getHeaderEventKey());
        if (!webhookHeader.getHeaderEventExpectedValue().equals(eventType)) {
            return HttpResponse.notModified();
        }

        if (body == null) {
            return HttpResponse.badRequest();
        }

        if (gitProperties.getWebhook().getSecret().isPresent()) {
            final var signature = request.getHeaders().get(webhookHeader.getHeaderSignatureKey());
            if (StringUtils.isEmpty(signature)) {
                return HttpResponse.badRequest();
            }

            final var secret = gitProperties.getWebhook().getSecret().get();
            if (verifySignature(signature, body, secret)) {
                triggerPullingIfNeeded(body);
            }
        } else {
            triggerPullingIfNeeded(body);
        }

        return HttpResponse.accepted();
    }

    private WebhookHeader getWebhookHeader() {
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

    private void triggerPullingIfNeeded(final String body) {
        // GITHUB https://docs.github.com/en/webhooks/webhook-events-and-payloads
        // BITBUCKET https://support.atlassian.com/bitbucket-cloud/docs/event-payloads/#Push
        // todo check that a push was one in the branch configured in GitProperties
        try {
            final JsonNode jsonNode = objectMapper.readTree(body);
            final Optional<String> branchName = switch (gitProperties.getWebhook().getType()) {
                case GITHUB -> Optional.of(jsonNode.get("ref").asText());
                case BITBUCKET -> {
                    final var changes = jsonNode.get("push").withArrayProperty("changes").elements();
                    while (changes.hasNext()) {
                        final var next = changes.next();
                        if (next.has("new")) {
                            yield Optional.of(next.get("new").get("name").asText());
                        }
                    }
                    yield Optional.empty();
                }
                case CUSTOM -> Optional.empty();
            };

            branchName
                    .map(v -> v.replace("refs/heads/", "")) // fixme
                    .ifPresent(branch -> {
                        if (branch.equals(gitProperties.getBranch())) {
                            log.info("{} Webhook triggered for branchName: {}", gitProperties.getWebhook().getType(), branch);
                            gitPull.pull(WEBHOOK);
                        }
                    });

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
