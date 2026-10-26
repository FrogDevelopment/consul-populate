package com.frogdevelopment.consul.populate.git.endpoint.handlers;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.frogdevelopment.consul.populate.git.GitProperties;

import io.micronaut.context.exceptions.ConfigurationException;

class WebhookSecretValidatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRefuseToStart_whenSecretIsMissingOrBlank(final String secret) {
        // given
        final var properties = propertiesWith(secret, false);

        // when / then
        assertThatThrownBy(() -> new WebhookSecretValidator(properties))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("consul.git.webhook.secret")
                .hasMessageContaining("allow-unsigned");
    }

    @Test
    void shouldStart_whenSecretIsConfigured() {
        // given
        final var properties = propertiesWith("my-secret", false);

        // when / then
        assertThatCode(() -> new WebhookSecretValidator(properties)).doesNotThrowAnyException();
    }

    @Test
    void shouldStart_whenUnsignedWebhooksAreExplicitlyAllowed() {
        // given
        final var properties = propertiesWith(null, true);

        // when / then
        assertThatCode(() -> new WebhookSecretValidator(properties)).doesNotThrowAnyException();
    }

    @Test
    void shouldStart_whenNoWebhookIsConfigured() {
        // given
        final var properties = new GitProperties();

        // when / then
        assertThatCode(() -> new WebhookSecretValidator(properties)).doesNotThrowAnyException();
    }

    private static GitProperties propertiesWith(final String secret, final boolean allowUnsigned) {
        final var webhook = new GitProperties.Webhook();
        webhook.setSecret(secret);
        webhook.setAllowUnsigned(allowUnsigned);
        final var properties = new GitProperties();
        properties.setWebhook(webhook);
        return properties;
    }
}
