package com.frogdevelopment.consul.populate.git.endpoint.handlers.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class GithubWebhookPayloadHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GithubWebhookPayloadHandler handler = new GithubWebhookPayloadHandler();

    @Test
    void getEventHeaderKey_shouldReturnGithubEventHeader() {
        assertThat(handler.getEventHeaderKey()).isEqualTo("X-GitHub-Event");
    }

    @Test
    void getExpectedEventValue_shouldReturnPush() {
        assertThat(handler.getExpectedEventValue()).isEqualTo("push");
    }

    @Test
    void getSignatureHeaderKey_shouldReturnHubSignature256() {
        assertThat(handler.getSignatureHeaderKey()).isEqualTo("X-Hub-Signature-256");
    }

    @Test
    void extractBranchName_shouldExtractBranchFromRef() {
        // given
        var payload = objectMapper.createObjectNode();
        payload.put("ref", "refs/heads/main");

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isPresent().hasValue("refs/heads/main");
    }

    @Test
    void extractBranchName_shouldReturnEmptyWhenRefIsMissing() {
        // given
        var payload = objectMapper.createObjectNode();
        payload.put("other_field", "value");

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractBranchName_shouldReturnEmptyWhenRefIsNull() {
        // given
        var payload = objectMapper.createObjectNode();
        payload.putNull("ref");

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void extractBranchName_shouldHandleFeatureBranch() {
        // given
        var payload = objectMapper.createObjectNode();
        payload.put("ref", "refs/heads/feature/my-feature");

        // when
        var result = handler.extractBranchName(payload);

        // then
        assertThat(result).isPresent().hasValue("refs/heads/feature/my-feature");
    }
}
