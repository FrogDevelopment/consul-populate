package com.frogdevelopment.consul.populate.git.endpoint.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.consul.populate.git.GitProperties;
import com.frogdevelopment.consul.populate.git.pull.GitPullJob;

import io.micronaut.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TogglePollHandlerTest {

    @Mock
    private GitProperties gitProperties;
    @Mock
    private GitPullJob gitPullJob;

    @InjectMocks
    private TogglePollHandler togglePollHandler;

    @Test
    void handle_shouldEnablePollingAndStartJob_whenEnableIsTrue() {
        // given
        given(gitProperties.isPollEnabled()).willReturn(false);

        // when
        var response = togglePollHandler.handle();

        // then
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        then(gitProperties).should().setPollEnabled(true);
        then(gitPullJob).should().start();
        then(gitPullJob).should(never()).stop();
    }

    @Test
    void handle_shouldDisablePollingAndStopJob_whenEnableIsFalse() {
        // given
        given(gitProperties.isPollEnabled()).willReturn(true);

        // when
        var response = togglePollHandler.handle();

        // then
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        then(gitProperties).should().setPollEnabled(false);
        then(gitPullJob).should().stop();
        then(gitPullJob).should(never()).start();
    }
}
