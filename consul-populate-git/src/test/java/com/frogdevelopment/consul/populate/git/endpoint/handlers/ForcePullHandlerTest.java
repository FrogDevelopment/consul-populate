package com.frogdevelopment.consul.populate.git.endpoint.handlers;

import static com.frogdevelopment.consul.populate.git.pull.Trigger.FORCED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.consul.populate.git.pull.GitPull;

import io.micronaut.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ForcePullHandlerTest {

    @Mock
    private GitPull gitPull;

    @InjectMocks
    private ForcePullHandler forcePullHandler;

    @Test
    void handle_shouldTriggerForcedPull() {
        // when
        forcePullHandler.handle();

        // then
        then(gitPull).should().pull(FORCED);
    }

    @Test
    void handle_shouldReturnOkResponse() {
        // when
        var response = forcePullHandler.handle();

        // then
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }
}
