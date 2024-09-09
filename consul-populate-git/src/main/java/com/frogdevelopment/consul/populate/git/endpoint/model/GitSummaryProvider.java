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

/**
 * Provider for generating {@link GitSummary} instances containing the current state
 * of the Git repository and pull configuration.
 *
 * <p>This provider aggregates information from multiple sources to create a comprehensive
 * summary of the Git-based configuration synchronization state, including:
 * <ul>
 *   <li>Repository information (URI, branch, local path, HEAD commit)</li>
 *   <li>Pull status (scheduled polling state, last pull details)</li>
 *   <li>File import configuration</li>
 * </ul>
 *
 * <p>Credentials in the repository URI are automatically masked in the summary
 * to prevent accidental exposure of sensitive information.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 * @see GitSummary
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class GitSummaryProvider {

    private final Git git;
    private final GitPull gitPull;
    private final GitProperties gitProperties;
    private final RepositoryDirectoryProvider repositoryDirectoryProvider;

    /**
     * Generates a summary of the current Git repository state.
     *
     * <p>Collects repository information including HEAD commit details,
     * working directory status (dirty/clean), pull configuration, and
     * file import settings.
     *
     * <p>Errors during repository information gathering are logged but do not
     * cause the method to fail; instead, partial information is returned.
     *
     * @return a {@link GitSummary} containing the current state
     */
    public GitSummary generateSummary() {
        final var repoSummary = createRepoInfo();
        final var pollingSummary = createPullSummary();
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
            log.warn("Error while masking URI credentials", e);
        }

        return new GitSummary.Repo(
                maskedUri,
                gitProperties.getBranch(),
                repositoryDirectoryProvider.getRepository().toString(),
                dirty,
                head
        );
    }

    private GitSummary.Pull createPullSummary() {
        return new GitSummary.Pull(
                gitProperties.isPollEnabled(),
                gitProperties.isPollEnabled() ? gitProperties.getPollInterval().toString() : null,
                gitPull.getTrigger(),
                gitPull.getLastPullTime(),
                gitPull.getLastPullDuration(),
                gitPull.getLastPullOutcome()
        );
    }
}
