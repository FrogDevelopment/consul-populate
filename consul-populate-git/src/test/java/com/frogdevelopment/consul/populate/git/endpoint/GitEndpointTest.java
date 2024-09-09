package com.frogdevelopment.consul.populate.git.endpoint;

import static com.frogdevelopment.consul.populate.git.endpoint.GitEndpoint.ACTION_FORCE_PULL;
import static com.frogdevelopment.consul.populate.git.endpoint.GitEndpoint.ACTION_TOGGLE_POLL;
import static com.frogdevelopment.consul.populate.git.endpoint.GitEndpoint.ACTION_WEBHOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.frogdevelopment.consul.populate.git.endpoint.handlers.ForcePullHandler;
import com.frogdevelopment.consul.populate.git.endpoint.handlers.TogglePollHandler;
import com.frogdevelopment.consul.populate.git.endpoint.handlers.WebhookHandler;
import com.frogdevelopment.consul.populate.git.endpoint.model.GitSummary;
import com.frogdevelopment.consul.populate.git.endpoint.model.GitSummaryProvider;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class GitEndpointTest {

    @Mock
    private GitSummaryProvider gitSummaryProvider;
    @Mock
    private TogglePollHandler togglePollHandler;
    @Mock
    private WebhookHandler webhookHandler;
    @Mock
    private ForcePullHandler forcePullHandler;
    @Mock
    private HttpRequest<?> httpRequest;

    @InjectMocks
    private GitEndpoint gitEndpoint;

    @Test
    void summary_shouldReturnGitSummaryFromProvider() {
        // given
        var expectedSummary = new GitSummary(null, null, null);
        given(gitSummaryProvider.generateSummary()).willReturn(expectedSummary);

        // when
        var result = gitEndpoint.summary();

        // then
        assertThat(result).isSameAs(expectedSummary);
        then(gitSummaryProvider).should().generateSummary();
    }

    @Test
    void handleGitActions_shouldDelegateToTogglePollHandler_whenActionIsTogglePoll() {
        // given
        var expectedResponse = HttpResponse.<Void>ok();
        given(togglePollHandler.handle()).willReturn(expectedResponse);

        // when
        var result = gitEndpoint.handleGitActions(ACTION_TOGGLE_POLL, httpRequest, null);

        // then
        assertThat(result).isSameAs(expectedResponse);
        then(togglePollHandler).should().handle();
    }

    @Test
    void handleGitActions_shouldDelegateToWebhookHandler_whenActionIsWebhook() {
        // given
        var body = "{\"ref\": \"refs/heads/main\"}";
        var expectedResponse = HttpResponse.<Void>accepted();
        given(webhookHandler.handle(httpRequest, body)).willReturn(expectedResponse);

        // when
        var result = gitEndpoint.handleGitActions(ACTION_WEBHOOK, httpRequest, body);

        // then
        assertThat(result).isSameAs(expectedResponse);
        then(webhookHandler).should().handle(httpRequest, body);
    }

    @Test
    void handleGitActions_shouldDelegateToForcePullHandler_whenActionIsForcePull() {
        // given
        var expectedResponse = HttpResponse.<Void>ok();
        given(forcePullHandler.handle()).willReturn(expectedResponse);

        // when
        var result = gitEndpoint.handleGitActions(ACTION_FORCE_PULL, httpRequest, null);

        // then
        assertThat(result).isSameAs(expectedResponse);
        then(forcePullHandler).should().handle();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "invalid", "push", "fetch", ""})
    void handleGitActions_shouldReturnNotFound_whenActionIsUnknown(String action) {
        // when
        var result = gitEndpoint.handleGitActions(action, httpRequest, null);

        // then
        assertThat(result.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }
}
