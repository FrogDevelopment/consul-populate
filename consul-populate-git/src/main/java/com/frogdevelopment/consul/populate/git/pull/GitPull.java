package com.frogdevelopment.consul.populate.git.pull;

import static com.frogdevelopment.consul.populate.git.pull.Status.FAILURE;
import static com.frogdevelopment.consul.populate.git.pull.Status.SUCCESS;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Singleton;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.eclipse.jgit.api.Git;

/**
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class GitPull {

    private final Git git;

    private final AtomicReference<Origin> lastPullOrigin = new AtomicReference<>(null);
    private final AtomicReference<Instant> lastPullTimeRef = new AtomicReference<>(null);
    private final AtomicReference<Duration> lastPullDurationRef = new AtomicReference<>(null);
    private final AtomicReference<Status> lastPullOutcomeRef = new AtomicReference<>(null);

    public void pull(final Origin origin) {
        try {
            lastPullOrigin.set(origin);
            log.debug("Pull repository: {}", origin);
            final var now = Instant.now();
            lastPullTimeRef.set(now);
            final var pullStatus = git.pull().call().isSuccessful() ? SUCCESS : FAILURE;
            lastPullDurationRef.set(Duration.between(now, Instant.now()));
            lastPullOutcomeRef.set(pullStatus);
            log.debug("Repository updated: {}", pullStatus);
        } catch (final Exception e) {
            log.error("Scheduled task encountered an error. Please check logs", e);
        }
    }

    public Origin getOrigin() {
        return lastPullOrigin.get();
    }

    public String getLastPullTime() {
        return Optional.ofNullable(lastPullTimeRef.get())
                .map(Instant::toString)
                .orElse(null);
    }

    public String getLastPullDuration() {
        return Optional.ofNullable(lastPullDurationRef.get())
                .map(Duration::toMillis)
                .map(millis -> DurationFormatUtils.formatDuration(millis , "ss.SSS's'"))
                .orElse(null);
    }

    public Status getLastPullOutcome() {
        return lastPullOutcomeRef.get();
    }
}
