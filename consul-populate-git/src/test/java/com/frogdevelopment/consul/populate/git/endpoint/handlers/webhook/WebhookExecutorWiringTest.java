package com.frogdevelopment.consul.populate.git.endpoint.handlers.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;

/**
 * {@code WebhookHandler} asks for the {@code blocking} executor to run the pull; the whole bean graph cannot be started
 * here (it clones a repository), so check that what it asks for can be resolved.
 */
class WebhookExecutorWiringTest {

    @Test
    void the_blocking_executor_that_the_webhook_handler_injects_exists() {
        try (var context = ApplicationContext.run(Map.of("consul.git.uri", "https://example.com/org/repo.git"))) {
            assertThat(context.containsBean(Executor.class, Qualifiers.byName(TaskExecutors.BLOCKING))).isTrue();
        }
    }
}
