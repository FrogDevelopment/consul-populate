package com.frogdevelopment.consul.populate.git.endpoint.handlers.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.micronaut.context.ApplicationContext;

/**
 * The handlers are selected by {@code @Requires} on the raw {@code consul.git.webhook.type} property, which ignores the
 * default of {@code GitProperties.Webhook}: these tests start a real context to check the selection.
 */
class WebhookPayloadHandlerSelectionTest {

    @Test
    void should_use_the_github_handler_when_no_type_is_configured() {
        // given - only the secret is set: the type defaults to GITHUB
        try (var context = start(Map.of("consul.git.webhook.secret", "my-secret"))) {
            // then
            assertThat(context.containsBean(GithubWebhookPayloadHandler.class)).isTrue();
            assertThat(context.containsBean(BitbucketWebhookPayloadHandler.class)).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GITHUB", "github"})
    void should_use_the_github_handler_in_upper_or_lower_case(final String type) {
        try (var context = start(Map.of("consul.git.webhook.type", type))) {
            assertThat(context.containsBean(GithubWebhookPayloadHandler.class)).isTrue();
            assertThat(context.containsBean(BitbucketWebhookPayloadHandler.class)).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"BITBUCKET", "bitbucket"})
    void should_use_the_bitbucket_handler_in_upper_or_lower_case(final String type) {
        try (var context = start(Map.of("consul.git.webhook.type", type))) {
            assertThat(context.containsBean(BitbucketWebhookPayloadHandler.class)).isTrue();
            assertThat(context.containsBean(GithubWebhookPayloadHandler.class)).isFalse();
        }
    }

    @Test
    void should_use_no_built_in_handler_when_the_type_is_custom() {
        try (var context = start(Map.of("consul.git.webhook.type", "CUSTOM"))) {
            assertThat(context.containsBean(GithubWebhookPayloadHandler.class)).isFalse();
            assertThat(context.containsBean(BitbucketWebhookPayloadHandler.class)).isFalse();
        }
    }

    private static ApplicationContext start(final Map<String, Object> webhookProperties) {
        final var properties = new HashMap<String, Object>(webhookProperties);
        properties.put("consul.git.uri", "https://example.com/org/repo.git");
        // an unsigned webhook would stop the startup: see WebhookSecretValidator
        properties.putIfAbsent("consul.git.webhook.secret", "my-secret");
        return ApplicationContext.run(properties);
    }
}
