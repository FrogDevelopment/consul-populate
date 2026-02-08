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

import org.eclipse.jgit.api.Git;

import com.frogdevelopment.consul.populate.PopulateService;
import com.frogdevelopment.consul.populate.git.GitProperties;

import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;

/**
 * Orchestrates git repository population and optional pull for changes.
 * Triggers Consul population and sets up scheduled pulls when pull is enabled.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class GitPullJob {

    private final GitProperties gitProperties;
    private final PopulateService populateService;
    @Named(TaskExecutors.SCHEDULED)
    private final TaskScheduler taskScheduler;
    private final Git git;
    private final GitPull gitPull;

    private final AtomicReference<ScheduledFuture<?>> scheduledFutureRef = new AtomicReference<>();
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    /**
     * Starts the pull job if not already running.
     * This method is idempotent - calling it multiple times has no effect if already started.
     */
    public void start() {
        final var existingFuture = scheduledFutureRef.get();
        if (existingFuture != null && !existingFuture.isDone()) {
            log.debug("ScheduledPoll job already running, ignoring start request");
            return;
        }

        stopping.set(false);

        log.info("Listening for index changes in repository");
        git.getRepository().getListenerList().addIndexChangedListener(indexEvent -> {
            log.info("Changes detected in repository");
            populateService.populate();
        });

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
    public void stop() {
        if (stopping.get()) {
            log.debug("ScheduledPoll job already stopped, ignoring stop request");
            return;
        }

        log.info("Stopping pull job");
        stopping.set(true);
        final var scheduledFuture = scheduledFutureRef.get();
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
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
