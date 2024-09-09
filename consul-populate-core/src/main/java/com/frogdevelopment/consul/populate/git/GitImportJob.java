package com.frogdevelopment.consul.populate.git;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;

import com.frogdevelopment.consul.populate.PopulateService;

import io.micronaut.scheduling.TaskScheduler;

@Slf4j
@RequiredArgsConstructor
public class GitImportJob implements AutoCloseable {

    private final GitProperties gitProperties;
    private final Path repositoryDirectory;
    private final PopulateService populateService;
    private final TaskScheduler taskScheduler;

    private Git git;

    public void init() throws GitAPIException {
        log.debug("Cloning repository [{}]", gitProperties.getUri());
        git = Git.cloneRepository()
                .setURI(gitProperties.getUri())
                .setDirectory(repositoryDirectory.toFile())
                .setGitDir(repositoryDirectory.resolve(Constants.DOT_GIT).toFile())
                .setBranch(gitProperties.getBranch())
                .setRemote(Constants.DEFAULT_REMOTE_NAME)
                .call();

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

    @Override
    public void close() {
        try {
            if (git != null) {
                git.close();
            }
        } catch (final Exception e) {
            log.error("Error closing git repository", e);
        }

        try {
            log.info("Deleting Git Repository...");
            FileUtils.deleteDirectory(repositoryDirectory.toFile());
        } catch (final IOException e) {
            log.error("Failed to delete repository", e);
        }
    }

}
