package com.frogdevelopment.consul.populate.git;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitPropertiesTest {

    @Test
    void toString_shouldNotContainTheSecrets() {
        // given
        final var webhook = new GitProperties.Webhook();
        webhook.setSecret("webhook-secret-value");
        final var properties = new GitProperties();
        properties.setUri("https://github.com/org/repo.git");
        properties.setToken("token-value");
        properties.setUsername("username-value");
        properties.setPassword("password-value");
        properties.setWebhook(webhook);

        // when - what anybody who logs the properties would print
        final var text = properties.toString();

        // then - the rest is still there to help
        assertThat(text).contains("https://github.com/org/repo.git").contains("username-value");
        assertThat(text).doesNotContain("token-value").doesNotContain("password-value").doesNotContain("webhook-secret-value");
    }
}
