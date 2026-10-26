package com.frogdevelopment.consul.populate.git.endpoint.handlers;

import lombok.extern.slf4j.Slf4j;

import com.frogdevelopment.consul.populate.git.GitProperties;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.StringUtils;

/**
 * Fails the application startup when a webhook is configured without a secret, instead of silently accepting unsigned
 * requests. A blank secret, for instance from {@code ${WEBHOOK_SECRET:}}, is treated as missing.
 *
 * <p>Set {@code consul.git.webhook.allow-unsigned=true} to explicitly accept unsigned webhooks.
 *
 * @author Le Gall Benoît
 * @since 1.4.0
 * @see GitProperties.Webhook
 */
@Slf4j
@Context
@Requires(property = "consul.git.webhook")
class WebhookSecretValidator {

    WebhookSecretValidator(final GitProperties gitProperties) {
        final var webhook = gitProperties.getWebhook();
        if (webhook == null || StringUtils.hasText(webhook.getSecret())) {
            return;
        }

        if (!webhook.isAllowUnsigned()) {
            throw new ConfigurationException(
                    "consul.git.webhook.secret is required to verify webhook signatures "
                            + "(or set consul.git.webhook.allow-unsigned=true to accept unsigned webhooks)");
        }
        log.warn("Webhook signature verification is disabled (consul.git.webhook.allow-unsigned=true): "
                + "anyone who can reach the endpoint can trigger a pull");
    }
}
