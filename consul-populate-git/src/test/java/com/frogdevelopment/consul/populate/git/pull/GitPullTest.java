package com.frogdevelopment.consul.populate.git.pull;

import static com.frogdevelopment.consul.populate.git.pull.Status.FAILURE;
import static com.frogdevelopment.consul.populate.git.pull.Status.SUCCESS;
import static com.frogdevelopment.consul.populate.git.pull.Trigger.FORCED;
import static com.frogdevelopment.consul.populate.git.pull.Trigger.SCHEDULED;
import static com.frogdevelopment.consul.populate.git.pull.Trigger.WEBHOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitPullTest {

    @Mock
    private Git git;
    @Mock
    private PullCommand pullCommand;
    @Mock
    private PullResult pullResult;

    @InjectMocks
    private GitPull gitPull;

    @Nested
    class Pull {

        @ParameterizedTest
        @EnumSource(Trigger.class)
        void pull_shouldSetTrigger(Trigger trigger) throws Exception {
            // given
            given(git.pull()).willReturn(pullCommand);
            given(pullCommand.call()).willReturn(pullResult);
            given(pullResult.isSuccessful()).willReturn(true);

            // when
            gitPull.pull(trigger);

            // then
            assertThat(gitPull.getTrigger()).isEqualTo(trigger);
        }

        @Test
        void pull_shouldSetLastPullTime() throws Exception {
            // given
            given(git.pull()).willReturn(pullCommand);
            given(pullCommand.call()).willReturn(pullResult);
            given(pullResult.isSuccessful()).willReturn(true);

            // when
            gitPull.pull(SCHEDULED);

            // then
            assertThat(gitPull.getLastPullTime()).isNotNull();
            assertThat(gitPull.getLastPullTime()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        }

        @Test
        void pull_shouldSetLastPullDuration() throws Exception {
            // given
            given(git.pull()).willReturn(pullCommand);
            given(pullCommand.call()).willReturn(pullResult);
            given(pullResult.isSuccessful()).willReturn(true);

            // when
            gitPull.pull(SCHEDULED);

            // then
            assertThat(gitPull.getLastPullDuration()).isNotNull();
            assertThat(gitPull.getLastPullDuration()).matches("\\d{2}\\.\\d{3}s");
        }

        @Test
        void pull_shouldSetSuccessOutcome_whenPullIsSuccessful() throws Exception {
            // given
            given(git.pull()).willReturn(pullCommand);
            given(pullCommand.call()).willReturn(pullResult);
            given(pullResult.isSuccessful()).willReturn(true);

            // when
            gitPull.pull(FORCED);

            // then
            assertThat(gitPull.getLastPullOutcome()).isEqualTo(SUCCESS);
        }

        @Test
        void pull_shouldSetFailureOutcome_whenPullIsNotSuccessful() throws Exception {
            // given
            given(git.pull()).willReturn(pullCommand);
            given(pullCommand.call()).willReturn(pullResult);
            given(pullResult.isSuccessful()).willReturn(false);

            // when
            gitPull.pull(WEBHOOK);

            // then
            assertThat(gitPull.getLastPullOutcome()).isEqualTo(FAILURE);
        }

        @Test
        void pull_shouldCallGitPull() throws Exception {
            // given
            given(git.pull()).willReturn(pullCommand);
            given(pullCommand.call()).willReturn(pullResult);
            given(pullResult.isSuccessful()).willReturn(true);

            // when
            gitPull.pull(SCHEDULED);

            // then
            then(git).should().pull();
            then(pullCommand).should().call();
        }

        @Test
        void pull_shouldHandleException_gracefully() throws Exception {
            // given
            given(git.pull()).willReturn(pullCommand);
            given(pullCommand.call()).willThrow(new RuntimeException("Git error"));

            // when
            gitPull.pull(FORCED);

            // then - no exception thrown, trigger is still set
            assertThat(gitPull.getTrigger()).isEqualTo(FORCED);
        }

        @Test
        void pull_shouldUpdateStatistics_onSubsequentPulls() throws Exception {
            // given
            given(git.pull()).willReturn(pullCommand);
            given(pullCommand.call()).willReturn(pullResult);
            given(pullResult.isSuccessful()).willReturn(true, false);

            // when - first pull
            gitPull.pull(SCHEDULED);
            var firstPullTime = gitPull.getLastPullTime();
            assertThat(gitPull.getLastPullOutcome()).isEqualTo(SUCCESS);

            // when - second pull
            gitPull.pull(WEBHOOK);

            // then - statistics updated
            assertThat(gitPull.getTrigger()).isEqualTo(WEBHOOK);
            assertThat(gitPull.getLastPullOutcome()).isEqualTo(FAILURE);
            assertThat(gitPull.getLastPullTime()).isNotNull();
        }
    }

    @Nested
    class Getters {

        @Test
        void getTrigger_shouldReturnNull_whenNoPullHasOccurred() {
            // when/then
            assertThat(gitPull.getTrigger()).isNull();
        }

        @Test
        void getLastPullTime_shouldReturnNull_whenNoPullHasOccurred() {
            // when/then
            assertThat(gitPull.getLastPullTime()).isNull();
        }

        @Test
        void getLastPullDuration_shouldReturnNull_whenNoPullHasOccurred() {
            // when/then
            assertThat(gitPull.getLastPullDuration()).isNull();
        }

        @Test
        void getLastPullOutcome_shouldReturnNull_whenNoPullHasOccurred() {
            // when/then
            assertThat(gitPull.getLastPullOutcome()).isNull();
        }
    }
}
