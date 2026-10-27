package com.frogdevelopment.consul.populate.git.endpoint.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.frogdevelopment.consul.populate.git.GitProperties;
import com.frogdevelopment.consul.populate.git.pull.GitPullJob;

/**
 * A toggle reads the value, flips it, and starts or stops the job: two requests at once must not lose one.
 */
class TogglePollHandlerConcurrencyTest {

    @Test
    void should_end_in_the_state_given_by_the_number_of_toggles() throws Exception {
        // given - an even number of toggles from several threads, starting with polling disabled
        final var properties = new GitProperties();
        final var job = mock(GitPullJob.class);
        final var handler = new TogglePollHandler(properties, job);
        final var threads = 8;
        final var togglesPerThread = 500;
        final var start = new CountDownLatch(1);

        // when
        try (var executor = Executors.newFixedThreadPool(threads)) {
            final var calls = new ArrayList<Future<?>>();
            for (var i = 0; i < threads; i++) {
                calls.add(executor.submit(() -> {
                    start.await();
                    for (var j = 0; j < togglesPerThread; j++) {
                        handler.handle();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (final var call : calls) {
                call.get(60, TimeUnit.SECONDS);
            }
        }

        // then - no toggle was lost: back to the initial state, with as many starts as stops
        assertThat(properties.isPollEnabled()).isFalse();
        verify(job, times(threads * togglesPerThread / 2)).start();
        verify(job, times(threads * togglesPerThread / 2)).stop();
    }
}
