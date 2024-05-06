package com.frogdevelopment.consul.populate;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;

import io.vertx.core.Future;

/**
 * Util class to help use the {@link io.vertx.core.Vertx}
 *
 * @author Le Gall Benoît
 * @see Future
 * @since 1.0.0
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VertxUtils {

    /**
     * Util method to help use the Vertx {@link Future} as a blocking call
     *
     * @param future future to execute as a blocking code
     * @param <T>    type of the result
     * @return the result value
     * @see Future
     * @see java.util.concurrent.CompletionStage
     */
    public static <T> T toBlocking(final Future<T> future) {
        try {
            return future.toCompletionStage().toCompletableFuture().get();
        } catch (final InterruptedException e) {
            log.warn("Interrupted!", e);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (final ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }
}
