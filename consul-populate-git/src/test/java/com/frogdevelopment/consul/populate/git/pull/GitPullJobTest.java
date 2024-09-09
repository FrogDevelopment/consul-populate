package com.frogdevelopment.consul.populate.git.pull;

import static com.frogdevelopment.consul.populate.git.pull.Trigger.SCHEDULED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.times;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.events.IndexChangedEvent;
import org.eclipse.jgit.events.IndexChangedListener;
import org.eclipse.jgit.events.ListenerList;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.consul.populate.PopulateService;
import com.frogdevelopment.consul.populate.git.GitProperties;

import io.micronaut.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class GitPullJobTest {

    @Mock
    private GitProperties gitProperties;
    @Mock
    private PopulateService populateService;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private Git git;
    @Mock
    private Repository repository;
    @Mock
    private ListenerList listenerList;
    @Mock
    private GitPull gitPull;
    @Mock
    private ScheduledFuture<?> scheduledFuture;
    @Mock
    private IndexChangedEvent indexChangedEvent;

    @Captor
    private ArgumentCaptor<IndexChangedListener> indexChangedListenerCaptor;
    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    @InjectMocks
    private GitPullJob gitPullJob;

    @Nested
    class Start {

        @BeforeEach
        void setUp() {
            given(git.getRepository()).willReturn(repository);
            given(repository.getListenerList()).willReturn(listenerList);
            given(gitProperties.getPollInterval()).willReturn(Duration.ofMinutes(5));
            willReturn(scheduledFuture).given(taskScheduler).scheduleWithFixedDelay(any(), any(), any(Runnable.class));
        }

        @Test
        void start_shouldScheduleTask_whenNotRunning() {
            // when
            gitPullJob.start();

            // then
            then(taskScheduler).should().scheduleWithFixedDelay(eq(Duration.ZERO), eq(Duration.ofMinutes(5)), any(Runnable.class));
        }

        @Test
        void start_shouldBeIdempotent_whenAlreadyRunning() {
            // given
            given(scheduledFuture.isDone()).willReturn(false);
            gitPullJob.start(); // First call

            // when
            gitPullJob.start(); // Second call

            // then
            then(taskScheduler).should(times(1)).scheduleWithFixedDelay(any(), any(), any(Runnable.class));
        }

        @Test
        void start_shouldReschedule_whenPreviousTaskIsDone() {
            // given
            given(scheduledFuture.isDone()).willReturn(true);
            gitPullJob.start(); // First call

            // when
            gitPullJob.start(); // Second call after previous is done

            // then
            then(taskScheduler).should(times(2)).scheduleWithFixedDelay(any(), any(), any(Runnable.class));
        }

        @Test
        void start_shouldRegisterIndexChangedListener_thatTriggersPopulate() {
            // when
            gitPullJob.start();

            // then
            then(listenerList).should().addIndexChangedListener(indexChangedListenerCaptor.capture());

            // when - simulate index change event
            indexChangedListenerCaptor.getValue().onIndexChanged(indexChangedEvent);

            // then
            then(populateService).should().populate();
        }

        @Test
        void start_shouldResetStoppingFlag_whenRestartingAfterStop() {
            // given
            given(scheduledFuture.isDone()).willReturn(false, true, false);
            gitPullJob.start();
            gitPullJob.stop();
            assertThat(gitPullJob.isRunning()).isFalse();

            // when - restart
            gitPullJob.start();

            // then - should be running again
            assertThat(gitPullJob.isRunning()).isTrue();
        }
    }

    @Nested
    class Pull {

        @BeforeEach
        void setUp() {
            given(git.getRepository()).willReturn(repository);
            given(repository.getListenerList()).willReturn(listenerList);
            given(gitProperties.getPollInterval()).willReturn(Duration.ofMinutes(5));
            willReturn(scheduledFuture).given(taskScheduler).scheduleWithFixedDelay(any(), any(), runnableCaptor.capture());
        }

        @Test
        void pull_shouldCallGitPull_whenNotStopping() {
            // given
            gitPullJob.start();

            // when - execute the scheduled task
            runnableCaptor.getValue().run();

            // then
            then(gitPull).should().pull(SCHEDULED);
        }

        @Test
        void pull_shouldSkipGitPull_whenStopping() {
            // given
            gitPullJob.start();
            gitPullJob.stop();

            // when - execute the scheduled task
            runnableCaptor.getValue().run();

            // then
            then(gitPull).shouldHaveNoInteractions();
        }
    }

    @Nested
    class Stop {

        private void setUpForRunningJob() {
            given(git.getRepository()).willReturn(repository);
            given(repository.getListenerList()).willReturn(listenerList);
            given(gitProperties.getPollInterval()).willReturn(Duration.ofMinutes(5));
            willReturn(scheduledFuture).given(taskScheduler).scheduleWithFixedDelay(any(), any(), any(Runnable.class));
        }

        @Test
        void stop_shouldCancelTask_whenRunning() {
            // given
            setUpForRunningJob();
            gitPullJob.start();

            // when
            gitPullJob.stop();

            // then
            then(scheduledFuture).should().cancel(true);
        }

        @Test
        void stop_shouldBeIdempotent_whenAlreadyStopped() {
            // given
            setUpForRunningJob();
            gitPullJob.start();
            gitPullJob.stop(); // First stop

            // when
            gitPullJob.stop(); // Second stop

            // then
            then(scheduledFuture).should(times(1)).cancel(true);
        }

        @Test
        void stop_shouldDoNothing_whenNeverStarted() {
            // when
            gitPullJob.stop();

            // then - no exception, nothing happens
            then(scheduledFuture).shouldHaveNoInteractions();
        }
    }

    @Nested
    class IsRunning {

        private void setUpForRunningJob() {
            given(git.getRepository()).willReturn(repository);
            given(repository.getListenerList()).willReturn(listenerList);
            given(gitProperties.getPollInterval()).willReturn(Duration.ofMinutes(5));
            willReturn(scheduledFuture).given(taskScheduler).scheduleWithFixedDelay(any(), any(), any(Runnable.class));
        }

        @Test
        void isRunning_shouldReturnFalse_whenNeverStarted() {
            // when/then
            assertThat(gitPullJob.isRunning()).isFalse();
        }

        @Test
        void isRunning_shouldReturnTrue_whenStartedAndNotDone() {
            // given
            setUpForRunningJob();
            given(scheduledFuture.isDone()).willReturn(false);
            gitPullJob.start();

            // when/then
            assertThat(gitPullJob.isRunning()).isTrue();
        }

        @Test
        void isRunning_shouldReturnFalse_whenStopped() {
            // given
            setUpForRunningJob();
            given(scheduledFuture.isDone()).willReturn(false);
            gitPullJob.start();
            gitPullJob.stop();

            // when/then
            assertThat(gitPullJob.isRunning()).isFalse();
        }

        @Test
        void isRunning_shouldReturnFalse_whenTaskIsDone() {
            // given
            setUpForRunningJob();
            gitPullJob.start();
            given(scheduledFuture.isDone()).willReturn(true);

            // when/then
            assertThat(gitPullJob.isRunning()).isFalse();
        }
    }
}
