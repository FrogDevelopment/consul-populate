package com.frogdevelopment.consul.populate.git.events;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.consul.populate.PopulateService;
import com.frogdevelopment.consul.populate.git.GitProperties;
import com.frogdevelopment.consul.populate.git.pull.GitPullJob;

import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;

@ExtendWith(MockitoExtension.class)
class ServerEventsListenerTest {

    @Mock
    private PopulateService populateService;
    @Mock
    private GitProperties gitProperties;
    @Mock
    private GitPullJob gitPullJob;
    @Mock
    private ServerStartupEvent serverStartupEvent;
    @Mock
    private ServerShutdownEvent serverShutdownEvent;

    @InjectMocks
    private ServerEventsListener serverEventsListener;

    @Nested
    class OnServerStartupEvent {

        @Test
        void shouldPopulateConsulAndStartPullJob_whenPollingEnabled() {
            // given
            given(gitProperties.isPollEnabled()).willReturn(true);

            // when
            serverEventsListener.onServerStartupEvent(serverStartupEvent);

            // then
            then(populateService).should().populate();
            then(gitPullJob).should().start();
        }

        @Test
        void shouldPopulateConsulWithoutStartingPullJob_whenPollingDisabled() {
            // given
            given(gitProperties.isPollEnabled()).willReturn(false);

            // when
            serverEventsListener.onServerStartupEvent(serverStartupEvent);

            // then
            then(populateService).should().populate();
            then(gitPullJob).should(never()).start();
        }
    }

    @Nested
    class OnServerShutdownEvent {

        @Test
        void shouldStopPullJob_whenJobIsRunning() {
            // given
            given(gitPullJob.isRunning()).willReturn(true);

            // when
            serverEventsListener.onServerShutdownEvent(serverShutdownEvent);

            // then
            then(gitPullJob).should().stop();
        }

        @Test
        void shouldNotStopPullJob_whenJobIsNotRunning() {
            // given
            given(gitPullJob.isRunning()).willReturn(false);

            // when
            serverEventsListener.onServerShutdownEvent(serverShutdownEvent);

            // then
            then(gitPullJob).should(never()).stop();
        }
    }
}
