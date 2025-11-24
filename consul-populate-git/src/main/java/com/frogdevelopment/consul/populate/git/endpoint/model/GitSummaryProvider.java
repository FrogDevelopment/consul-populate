package com.frogdevelopment.consul.populate.git.endpoint.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;

import com.frogdevelopment.consul.populate.git.GitProperties;
import com.frogdevelopment.consul.populate.git.RepositoryDirectoryProvider;
import com.frogdevelopment.consul.populate.git.pull.GitPull;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class GitSummaryProvider {

    private final Git git;
    private final GitPull gitPull;
    private final GitProperties gitProperties;
    private final RepositoryDirectoryProvider repositoryDirectoryProvider;

    public GitSummary generateSummary() {
        final var repoSummary = createRepoInfo();
        final var pollingSummary = createPollingSummary();
        return new GitSummary(repoSummary,
                pollingSummary,
                gitProperties.getFileProperties());
    }

    private GitSummary.Repo createRepoInfo() {
        // Build head info
        GitSummary.Head head = null;
        var dirty = false;
        try {
            final var repo = git.getRepository();
            try (final var walk = new RevWalk(repo)) {
                final var headId = repo.resolve(Constants.HEAD);
                if (headId != null) {
                    final var commit = walk.parseCommit(headId);
                    final var ident = commit.getAuthorIdent();
                    head = new GitSummary.Head(
                            commit.getId().name(),
                            commit.getId().abbreviate(7).name(),
                            commit.getShortMessage(),
                            ident.getWhenAsInstant().toString()
                    );
                }
            }
            dirty = !git.status().call().isClean();
        } catch (final Exception e) {
            // Keep head as null and dirty as false on errors to avoid failing the endpoint
            log.error("Error while trying to gather repository info", e);
        }

        // Mask potential credentials in URI
        var maskedUri = gitProperties.getUri();
        try {
            maskedUri = new URIish(gitProperties.getUri()).setUser(null).setPass(null).toString();
        } catch (final Exception e) {
            // keep original uri if masking fails
            log.warn("Error while trying to gather repository info", e);
        }

        return new GitSummary.Repo(
                maskedUri,
                gitProperties.getBranch(),
                repositoryDirectoryProvider.getRepository().toString(),
                gitProperties.isSslVerify(),
                dirty,
                head
        );
    }

    private GitSummary.Polling createPollingSummary() {
        final var pollingProps = gitProperties.getPolling();

        return new GitSummary.Polling(
                pollingProps.isEnabled(),
                pollingProps.getDelay().toString(),
                gitPull.getOrigin(),
                gitPull.getLastPullTime(),
                gitPull.getLastPullDuration(),
                gitPull.getLastPullOutcome()
        );
    }
}
