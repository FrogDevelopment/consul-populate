package com.frogdevelopment.consul.populate.git.pull;

import static com.frogdevelopment.consul.populate.git.pull.Status.FAILURE;
import static com.frogdevelopment.consul.populate.git.pull.Status.SUCCESS;
import static com.frogdevelopment.consul.populate.git.pull.Trigger.FORCED;
import static com.frogdevelopment.consul.populate.git.pull.Trigger.SCHEDULED;
import static com.frogdevelopment.consul.populate.git.pull.Trigger.WEBHOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.consul.populate.PopulateService;
import com.frogdevelopment.consul.populate.git.GitProperties;

/**
 * Uses real repositories (an origin and its clone) rather than mocks: what matters here is what JGit does to the
 * working tree, e.g. after a rewritten history.
 */
@ExtendWith(MockitoExtension.class)
class GitPullTest {

    private static final String FILE = "application.yaml";
    private static final PersonIdent IDENT = new PersonIdent("test", "test@example.com");

    @TempDir
    Path tempDir;

    @Mock
    private PopulateService populateService;

    private Git origin;
    private Git clone;
    private Path cloneDir;
    private GitPull gitPull;

    @BeforeEach
    void setUp() throws Exception {
        final var originDir = tempDir.resolve("origin");
        origin = Git.init().setDirectory(originDir.toFile()).setInitialBranch("main").call();
        commit(origin, "v: 1");

        cloneDir = tempDir.resolve("clone");
        clone = Git.cloneRepository()
                .setURI(originDir.toString())
                .setDirectory(cloneDir.toFile())
                .setBranch("main")
                .call();

        gitPull = new GitPull(clone, new GitProperties(), populateService);
    }

    @AfterEach
    void tearDown() {
        clone.close();
        origin.close();
    }

    @Nested
    class Pull {

        @ParameterizedTest
        @EnumSource(Trigger.class)
        void shouldSetTrigger(final Trigger trigger) {
            // when
            gitPull.pull(trigger);

            // then
            assertThat(gitPull.getTrigger()).isEqualTo(trigger);
        }

        @Test
        void shouldRecordTimeDurationAndOutcome() {
            // when
            final var status = gitPull.pull(SCHEDULED);

            // then
            assertThat(status).isEqualTo(SUCCESS);
            assertThat(gitPull.getLastPullOutcome()).isEqualTo(SUCCESS);
            assertThat(gitPull.getLastPullTime()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
            assertThat(gitPull.getLastPullDuration()).matches("\\d{2}\\.\\d{3}s");
        }

        @Test
        void shouldUpdateClone_andPopulate_whenRemoteHasANewCommit() throws Exception {
            // given
            gitPull.populate();
            commit(origin, "v: 2");

            // when
            final var status = gitPull.pull(WEBHOOK);

            // then
            assertThat(status).isEqualTo(SUCCESS);
            assertThat(Files.readString(cloneDir.resolve(FILE))).isEqualTo("v: 2");
            verify(populateService, times(2)).populate();
        }

        @Test
        void shouldNotPopulate_whenNothingChangedSinceTheLastPopulation() {
            // given
            gitPull.populate();

            // when
            final var status = gitPull.pull(SCHEDULED);

            // then
            assertThat(status).isEqualTo(SUCCESS);
            verify(populateService, times(1)).populate();
        }

        @Test
        void shouldPopulate_whenNothingWasPopulatedYet() {
            // when
            final var status = gitPull.pull(FORCED);

            // then
            assertThat(status).isEqualTo(SUCCESS);
            verify(populateService, times(1)).populate();
        }

        @Test
        void shouldFollowARewrittenHistory_withoutMergeCommitOrConflict() throws Exception {
            // given - the clone follows c2, then origin drops c2 and publishes a different c3 on top of c1
            commit(origin, "v: 2");
            gitPull.pull(SCHEDULED);
            origin.reset().setMode(ResetType.HARD).setRef("HEAD~1").call();
            final var rewritten = commit(origin, "v: 3");

            // when
            final var status = gitPull.pull(SCHEDULED);

            // then - a merge commit would have a different id than the commit published by origin
            assertThat(status).isEqualTo(SUCCESS);
            assertThat(clone.getRepository().resolve(Constants.HEAD)).isEqualTo(rewritten.getId());
            assertThat(Files.readString(cloneDir.resolve(FILE))).isEqualTo("v: 3");
        }

        @Test
        void shouldDiscardLocalModifications() throws Exception {
            // given
            Files.writeString(cloneDir.resolve(FILE), "locally: modified");

            // when
            gitPull.pull(SCHEDULED);

            // then
            assertThat(Files.readString(cloneDir.resolve(FILE))).isEqualTo("v: 1");
        }

        @Test
        void shouldReturnFailure_andRecordIt_whenTheRemoteIsUnreachable() throws Exception {
            // given
            final var config = clone.getRepository().getConfig();
            config.setString("remote", Constants.DEFAULT_REMOTE_NAME, "url", tempDir.resolve("missing").toString());
            config.save();

            // when
            final var status = gitPull.pull(WEBHOOK);

            // then
            assertThat(status).isEqualTo(FAILURE);
            assertThat(gitPull.getLastPullOutcome()).isEqualTo(FAILURE);
            assertThat(gitPull.getTrigger()).isEqualTo(WEBHOOK);
            assertThat(gitPull.getLastPullDuration()).isNotNull();
            verify(populateService, never()).populate();
        }

        @Test
        void shouldReturnFailure_whenPopulationFails_andRetryItOnTheNextPull() {
            // given
            willThrow(new IllegalStateException("Consul is not reachable"))
                    .willDoNothing()
                    .given(populateService).populate();

            // when
            final var first = gitPull.pull(SCHEDULED);
            final var second = gitPull.pull(SCHEDULED);

            // then - nothing changed upstream, yet the second pull populates because the first one failed
            assertThat(first).isEqualTo(FAILURE);
            assertThat(second).isEqualTo(SUCCESS);
            assertThat(gitPull.getLastPullOutcome()).isEqualTo(SUCCESS);
            verify(populateService, times(2)).populate();
        }

        @Test
        void shouldUpdateStatistics_onSubsequentPulls() throws Exception {
            // given
            gitPull.pull(SCHEDULED);
            assertThat(gitPull.getLastPullOutcome()).isEqualTo(SUCCESS);

            final var config = clone.getRepository().getConfig();
            config.setString("remote", Constants.DEFAULT_REMOTE_NAME, "url", tempDir.resolve("missing").toString());
            config.save();

            // when
            gitPull.pull(WEBHOOK);

            // then - the failure replaces the previous SUCCESS instead of leaving it next to the new trigger
            assertThat(gitPull.getTrigger()).isEqualTo(WEBHOOK);
            assertThat(gitPull.getLastPullOutcome()).isEqualTo(FAILURE);
        }
    }

    @Nested
    class Populate {

        @Test
        void shouldPropagateTheFailure() {
            // given
            willThrow(new IllegalStateException("Consul is not reachable")).given(populateService).populate();

            // when / then
            assertThatThrownBy(() -> gitPull.populate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Consul is not reachable");
        }

        @Test
        void shouldPopulateEvenIfAlreadyPopulatedForTheSameCommit() {
            // when
            gitPull.populate();
            gitPull.populate();

            // then
            verify(populateService, times(2)).populate();
        }
    }

    @Nested
    class Concurrency {

        @Test
        void shouldNeverRunTwoPopulationsAtTheSameTime() throws Exception {
            // given
            final var running = new AtomicInteger();
            final var maxRunning = new AtomicInteger();
            willAnswer(invocation -> {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                TimeUnit.MILLISECONDS.sleep(300);
                running.decrementAndGet();
                return null;
            }).given(populateService).populate();

            final var start = new CountDownLatch(1);
            try (final var executor = Executors.newFixedThreadPool(2)) {
                final var populating = executor.submit(() -> {
                    start.await();
                    gitPull.populate();
                    return null;
                });
                final var pulling = executor.submit(() -> {
                    start.await();
                    return gitPull.pull(SCHEDULED);
                });

                // when
                start.countDown();
                populating.get(10, TimeUnit.SECONDS);
                pulling.get(10, TimeUnit.SECONDS);
            }

            // then
            assertThat(maxRunning.get()).isEqualTo(1);
        }
    }

    private static RevCommit commit(final Git git, final String content) throws Exception {
        final var workTree = git.getRepository().getWorkTree().toPath();
        Files.writeString(workTree.resolve(FILE), content);
        git.add().addFilepattern(FILE).call();
        return git.commit()
                .setMessage(content)
                .setAuthor(IDENT)
                .setCommitter(IDENT)
                .setSign(false)
                .call();
    }
}
