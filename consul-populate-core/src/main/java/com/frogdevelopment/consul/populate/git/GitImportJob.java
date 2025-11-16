package com.frogdevelopment.consul.populate.git;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Named;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

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
@RequiredArgsConstructor
public class GitImportJob {

    private final GitProperties gitProperties;
    private final PopulateService populateService;
    @Named(TaskExecutors.SCHEDULED)
    private final TaskScheduler taskScheduler;
    private final Git git;

    public void init() throws GitAPIException {
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
        taskScheduler.scheduleWithFixedDelay(polling.getDelay(), polling.getDelay(), this::pull);
    }

    private void pull() {
        try {
            log.debug("Pull repository");
            final var result = git.pull()
                    .call();
            log.debug("Repository updated: {}", result.isSuccessful());
        } catch (final Exception e) {
            log.error("Scheduled task encountered an error. Please check logs", e);
        }
    }
}
