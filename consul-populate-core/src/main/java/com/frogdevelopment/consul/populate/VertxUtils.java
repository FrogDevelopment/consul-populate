package com.frogdevelopment.consul.populate;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;

import io.vertx.core.Future;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VertxUtils {

    public static <T> T toBlocking(final Future<T> future) {
        try {
            return future.toCompletionStage().toCompletableFuture().get();
        } catch (InterruptedException e) {
            log.warn("Interrupted!", e);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }
}
