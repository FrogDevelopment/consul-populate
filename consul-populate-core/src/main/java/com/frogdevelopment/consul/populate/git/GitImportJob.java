package com.frogdevelopment.consul.populate.git;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.eclipse.jgit.api.Git;

import com.frogdevelopment.consul.populate.PopulateService;

import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;

/**
 * Orchestrates git repository population and optional polling for changes.
 * Triggers Consul population and sets up scheduled pulls when polling is enabled.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class GitImportJob {

    private final GitProperties gitProperties;
    private final PopulateService populateService;
    @Named(TaskExecutors.SCHEDULED)
    private final TaskScheduler taskScheduler;
    private final Git git;

    private final AtomicReference<ScheduledFuture<?>> scheduledFutureRef = new AtomicReference<>();
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    public void start() {
        log.info("Populating Consul with repository");
        populateService.populate();

        if (gitProperties.getPolling().isEnabled()) {
            listenForRepositoryChanges();
        }
    }

    private void listenForRepositoryChanges() {
        log.info("Listening for index changes in repository");
        git.getRepository().getListenerList().addIndexChangedListener(indexEvent -> {
            log.info("Changes detected in repository");
            populateService.populate();
        });

        final var polling = gitProperties.getPolling();
        log.debug("Scheduling pull command with fixed delay={}", polling.getDelay());
        final var scheduledFuture = taskScheduler.scheduleWithFixedDelay(polling.getDelay(), polling.getDelay(), this::pull);
        scheduledFutureRef.set(scheduledFuture);
    }

    private void pull() {
        if (stopping.get()) {
            log.debug("Job stopped, skipping polling");
            return;
        }
        try {
            log.debug("Pull repository");
            final var result = git.pull()
                    .call();
            log.debug("Repository updated: {}", result.isSuccessful());
        } catch (final Exception e) {
            log.error("Scheduled task encountered an error. Please check logs", e);
        }
    }

    public void stop() {
        if (gitProperties.getPolling().isEnabled()) {
            log.info("Stoping polling job");
            stopping.set(true);
            final var scheduledFuture = scheduledFutureRef.get();
            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
            }
        }
    }
}
