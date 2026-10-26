package com.frogdevelopment.consul.populate.git.pull;

import static com.frogdevelopment.consul.populate.git.pull.Status.FAILURE;
import static com.frogdevelopment.consul.populate.git.pull.Status.SUCCESS;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.inject.Singleton;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

import com.frogdevelopment.consul.populate.PopulateService;
import com.frogdevelopment.consul.populate.git.GitProperties;

/**
 * Synchronizes the local clone with the remote branch and populates Consul when the content changed.
 *
 * <p>This class is responsible for:
 * <ul>
 *   <li>Fetching the remote and hard-resetting the clone to the configured branch, so a force-push or a rewritten
 *   history never leaves a merge commit or conflict markers in the working tree</li>
 *   <li>Populating Consul when the checked-out commit is not the one last populated, whatever the trigger
 *   (schedule, webhook or force-pull)</li>
 *   <li>Tracking the last pull trigger, time, duration, and outcome</li>
 * </ul>
 *
 * <p>Pulls and populates are serialized by a single lock: the working tree is never modified while it is being read,
 * and an older snapshot can never overwrite a newer one.
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
    private final GitProperties gitProperties;
    private final PopulateService populateService;

    private final ReentrantLock lock = new ReentrantLock();

    /** Commit whose content is in Consul; {@code null} until a populate succeeded. Guarded by {@link #lock}. */
    private final AtomicReference<ObjectId> lastPopulatedCommit = new AtomicReference<>(null);

    private final AtomicReference<Trigger> lastPullTrigger = new AtomicReference<>(null);
    private final AtomicReference<Instant> lastPullTimeRef = new AtomicReference<>(null);
    private final AtomicReference<Duration> lastPullDurationRef = new AtomicReference<>(null);
    private final AtomicReference<Status> lastPullOutcomeRef = new AtomicReference<>(null);

    /**
     * Populates Consul with the content of the current checkout, whether or not it changed since the last populate.
     *
     * <p>Meant for the initial population. A failure is propagated to the caller and leaves the repository marked as
     * not populated, so the next {@link #pull(Trigger)} retries even if nothing changed upstream.
     *
     * @throws RuntimeException if the population fails
     */
    public void populate() {
        lock.lock();
        try {
            final var head = head();
            populateService.populate();
            lastPopulatedCommit.set(head);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fetches the remote, resets the clone to the configured branch, and populates Consul if the checked-out commit
     * differs from the one last populated. The outcome is recorded and returned; this method never throws.
     *
     * @param trigger the source that triggered this pull operation
     * @return {@link Status#SUCCESS} if the clone is up to date and Consul reflects it, {@link Status#FAILURE} otherwise
     */
    public Status pull(final Trigger trigger) {
        lock.lock();
        try {
            lastPullTrigger.set(trigger);
            log.debug("Pull repository: {}", trigger);
            final var start = Instant.now();
            lastPullTimeRef.set(start);

            var status = FAILURE;
            try {
                fetchAndReset();
                populateIfChanged();
                status = SUCCESS;
            } catch (final Exception e) {
                log.error("Pull operation encountered an error. Please check logs", e);
            }

            lastPullDurationRef.set(Duration.between(start, Instant.now()));
            lastPullOutcomeRef.set(status);
            log.debug("Repository updated: {}", status);
            return status;
        } finally {
            lock.unlock();
        }
    }

    private void fetchAndReset() throws GitAPIException {
        git.fetch()
                .setRemote(Constants.DEFAULT_REMOTE_NAME)
                .call();
        git.reset()
                .setMode(ResetType.HARD)
                .setRef(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/" + gitProperties.getBranch())
                .call();
    }

    private void populateIfChanged() {
        final var head = head();
        if (head != null && head.equals(lastPopulatedCommit.get())) {
            log.debug("No change since the last population (commit {})", head.name());
            return;
        }

        log.info("Changes detected in repository, populating Consul");
        populateService.populate();
        lastPopulatedCommit.set(head);
    }

    private ObjectId head() {
        try {
            return git.getRepository().resolve(Constants.HEAD);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to resolve the HEAD of the repository", e);
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
     * Returns the duration of the last pull operation formatted as "ss.SSS's'".
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
