package com.frogdevelopment.consul.populate.git.pull;

import static com.frogdevelopment.consul.populate.git.pull.Trigger.SCHEDULED;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.git.GitProperties;

import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;

/**
 * Schedules the periodic {@link GitPull} when polling is enabled.
 * Populating Consul after a pull is handled by {@link GitPull}, so the webhook and force-pull triggers behave the same
 * whether or not this job is running.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class GitPullJob {

    private final GitProperties gitProperties;
    @Named(TaskExecutors.SCHEDULED)
    private final TaskScheduler taskScheduler;
    private final GitPull gitPull;

    private final AtomicReference<ScheduledFuture<?>> scheduledFutureRef = new AtomicReference<>();
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    /**
     * Starts the pull job if not already running.
     * This method is idempotent - calling it multiple times has no effect if already started. It is synchronized with
     * {@link #stop()}: two concurrent calls could otherwise both see no running job and schedule it twice, leaving a job
     * that nothing can cancel.
     */
    public synchronized void start() {
        final var existingFuture = scheduledFutureRef.get();
        if (existingFuture != null && !existingFuture.isDone()) {
            log.debug("ScheduledPoll job already running, ignoring start request");
            return;
        }

        stopping.set(false);

        final var interval = gitProperties.getPollInterval();
        log.debug("Scheduling pull command with fixed scheduledInterval={}", interval);
        final var scheduledFuture = taskScheduler.scheduleWithFixedDelay(Duration.ZERO, interval, this::pull);
        scheduledFutureRef.set(scheduledFuture);
    }

    private void pull() {
        if (stopping.get()) {
            log.debug("Job stopped, skipping pull");
            return;
        }

        gitPull.pull(SCHEDULED);
    }

    /**
     * Stops the pull job if currently running.
     * This method is idempotent - calling it multiple times has no effect if already stopped.
     */
    public synchronized void stop() {
        if (stopping.get()) {
            log.debug("ScheduledPoll job already stopped, ignoring stop request");
            return;
        }

        log.info("Stopping pull job");
        stopping.set(true);
        final var scheduledFuture = scheduledFutureRef.get();
        if (scheduledFuture != null) {
            // do not interrupt: a pull that is running (or the population it leads to) must end, only the next ones stop
            scheduledFuture.cancel(false);
        }
    }

    /**
     * Returns whether the pull job is currently running.
     *
     * @return true if the job is running, false otherwise
     */
    public boolean isRunning() {
        final var scheduledFuture = scheduledFutureRef.get();
        return scheduledFuture != null && !scheduledFuture.isDone() && !stopping.get();
    }
}
