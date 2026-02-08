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
 * Executes git pull operations and tracks pull statistics.
 *
 * <p>This class is responsible for:
 * <ul>
 *   <li>Executing git pull commands against the cloned repository</li>
 *   <li>Tracking the last pull trigger, time, duration, and outcome</li>
 *   <li>Thread-safe access to pull statistics via atomic references</li>
 * </ul>
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 * @see Trigger
 * @see Status
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class GitPull {

    private final Git git;

    private final AtomicReference<Trigger> lastPullTrigger = new AtomicReference<>(null);
    private final AtomicReference<Instant> lastPullTimeRef = new AtomicReference<>(null);
    private final AtomicReference<Duration> lastPullDurationRef = new AtomicReference<>(null);
    private final AtomicReference<Status> lastPullOutcomeRef = new AtomicReference<>(null);

    /**
     * Executes a git pull operation and records the result.
     *
     * @param trigger the source that triggered this pull operation
     */
    public void pull(final Trigger trigger) {
        try {
            lastPullTrigger.set(trigger);
            log.debug("Pull repository: {}", trigger);
            final var now = Instant.now();
            lastPullTimeRef.set(now);
            final var pullStatus = git.pull().call().isSuccessful() ? SUCCESS : FAILURE;
            lastPullDurationRef.set(Duration.between(now, Instant.now()));
            lastPullOutcomeRef.set(pullStatus);
            log.debug("Repository updated: {}", pullStatus);
        } catch (final Exception e) {
            log.error("Pull operation encountered an error. Please check logs", e);
        }
    }

    /**
     * Returns the trigger source of the last pull operation.
     *
     * @return the last pull trigger, or null if no pull has occurred
     */
    public Trigger getTrigger() {
        return lastPullTrigger.get();
    }

    /**
     * Returns the timestamp of the last pull operation as an ISO-8601 string.
     *
     * @return the last pull time, or null if no pull has occurred
     */
    public String getLastPullTime() {
        return Optional.ofNullable(lastPullTimeRef.get())
                .map(Instant::toString)
                .orElse(null);
    }

    /**
     * Returns the duration of the last pull operation formatted as "ss.SSS's".
     *
     * @return the last pull duration, or null if no pull has occurred
     */
    public String getLastPullDuration() {
        return Optional.ofNullable(lastPullDurationRef.get())
                .map(Duration::toMillis)
                .map(millis -> DurationFormatUtils.formatDuration(millis , "ss.SSS's'"))
                .orElse(null);
    }

    /**
     * Returns the outcome status of the last pull operation.
     *
     * @return the last pull outcome, or null if no pull has occurred
     */
    public Status getLastPullOutcome() {
        return lastPullOutcomeRef.get();
    }
}
