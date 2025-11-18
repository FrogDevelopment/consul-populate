package com.frogdevelopment.consul.populate.git.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;

import com.frogdevelopment.consul.populate.git.GitImportJob;
import com.frogdevelopment.consul.populate.git.GitProperties;
import com.frogdevelopment.consul.populate.git.RepositoryDirectoryProvider;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.micronaut.management.endpoint.annotation.Write;

@Slf4j
@Endpoint(value = "git", defaultSensitive = false)
@RequiredArgsConstructor
public class GitEndpoint {

    private final Git git;
    private final GitImportJob gitImportJob;
    private final GitProperties gitProperties;
    private final RepositoryDirectoryProvider repositoryDirectoryProvider;

    @Read
    public GitSummary summary() {
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
                gitImportJob.getLastPullTime(),
                gitImportJob.getLastPullDuration(),
                gitImportJob.getLastPullOutcome()
        );
    }

    @Write
    @Status(HttpStatus.OK)
    public HttpResponse<Void> handleGitActions(@Selector final String action,
                                 HttpRequest<?> request,
                                 @Nullable @Header("X-GitHub-Event") final String eventType,
                                 @Nullable @Header("X-Hub-Signature-256") final String signature,
                                 @QueryValue(defaultValue = "false") final boolean enable,
                                 @Nullable @Body final Object payload) {
        return switch (action) {
            case "polling" -> handlePolling(enable);
            case "webhook" -> handleWebhook(eventType, signature, payload);
            case "pull" -> handlePull();
            default -> {
                log.warn("invalid action {}", action);
                yield HttpResponse.notFound();
            }
        };
    }

    private HttpResponse<Void> handlePolling(final boolean enable) {
        log.warn("Git polling: {}", enable);
        return HttpResponse.ok();
    }

    private HttpResponse<Void> handleWebhook(@Nullable final String eventType,
                               @Nullable final String signature,
                               @Nullable final Object payload) {
        if (!"push".equals(eventType)) {// todo make the eventType parametrable ?
            return  HttpResponse.notModified();
        }
        verifySignature(signature, payload);

        log.warn("Git webhook: {} -> {}", eventType, payload);

        return HttpResponse.accepted();
    }

    private void verifySignature(String signature, Object payload) {
        final var secretToken = "secret given to GitHub for webhook";
        // fixme to do/ algorithm HMAC, hash SHA-256
        //  check if library exists

    }

    private HttpResponse<Void> handlePull() {
        log.warn("Git pull: {}", gitProperties.getBranch());

        return HttpResponse.accepted();
    }

}
