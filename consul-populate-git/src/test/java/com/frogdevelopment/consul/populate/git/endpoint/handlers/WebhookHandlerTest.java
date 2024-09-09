package com.frogdevelopment.consul.populate.git.endpoint.handlers;

import static com.frogdevelopment.consul.populate.git.pull.Trigger.WEBHOOK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frogdevelopment.consul.populate.git.GitProperties;
import com.frogdevelopment.consul.populate.git.endpoint.handlers.webhook.WebhookPayloadHandler;
import com.frogdevelopment.consul.populate.git.pull.GitPull;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class WebhookHandlerTest {

    @Mock
    private GitProperties gitProperties;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private GitPull gitPull;
    @Mock
    private WebhookPayloadHandler payloadHandler;
    @Mock
    private HttpRequest<?> httpRequest;
    @Mock
    private HttpHeaders httpHeaders;

    private WebhookHandler webhookHandler;

    @Nested
    class WhenWebhookNotConfigured {

        @BeforeEach
        void setUp() {
            webhookHandler = new WebhookHandler(gitProperties, objectMapper, gitPull, Optional.empty());
        }

        @Test
        void handle_shouldReturnNotModified_whenWebhookConfigIsNull() {
            // given
            given(gitProperties.getWebhook()).willReturn(null);

            // when
            var response = webhookHandler.handle(httpRequest, "body");

            // then
            assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NOT_MODIFIED.getCode());
            then(gitPull).should(never()).pull(any());
        }

        @Test
        void handle_shouldReturnNotImplemented_whenNoPayloadHandler() {
            // given
            var webhookConfig = new GitProperties.Webhook();
            webhookConfig.setType(GitProperties.Webhook.GitType.CUSTOM);
            given(gitProperties.getWebhook()).willReturn(webhookConfig);

            // when
            var response = webhookHandler.handle(httpRequest, "body");

            // then
            assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED.getCode());
            then(gitPull).should(never()).pull(any());
        }
    }

    @Nested
    class WhenWebhookConfigured {

        private GitProperties.Webhook webhookConfig;

        @BeforeEach
        void setUp() {
            given(httpRequest.getHeaders()).willReturn(httpHeaders);
            webhookHandler = new WebhookHandler(gitProperties, objectMapper, gitPull, Optional.of(payloadHandler));
            webhookConfig = new GitProperties.Webhook();
            given(gitProperties.getWebhook()).willReturn(webhookConfig);
            given(payloadHandler.getEventHeaderKey()).willReturn("X-GitHub-Event");
            given(payloadHandler.getExpectedEventValue()).willReturn("push");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"ping", "pull_request", "issues"})
        void handle_shouldReturnNotModified_whenEventTypeInvalid(String eventType) {
            // given
            given(httpHeaders.get("X-GitHub-Event")).willReturn(eventType);

            // when
            var response = webhookHandler.handle(httpRequest, "body");

            // then
            assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NOT_MODIFIED.getCode());
            then(gitPull).should(never()).pull(any());
        }

        @Test
        void handle_shouldReturnBadRequest_whenBodyIsNull() {
            // given
            given(httpHeaders.get("X-GitHub-Event")).willReturn("push");

            // when
            var response = webhookHandler.handle(httpRequest, null);

            // then
            assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
            then(gitPull).should(never()).pull(any());
        }

        @Nested
        class WhenSecretConfigured {

            @BeforeEach
            void setUp() {
                webhookConfig.setSecret("my-secret");
                given(httpHeaders.get("X-GitHub-Event")).willReturn("push");
                given(payloadHandler.getSignatureHeaderKey()).willReturn("X-Hub-Signature-256");
            }

            @ParameterizedTest
            @NullAndEmptySource
            @ValueSource(strings = {"sha256=invalid", "sha1=invalid", "md5=something"})
            void handle_shouldReturnUnauthorized_whenSignatureInvalid(String signature) {
                // given
                given(httpHeaders.get("X-Hub-Signature-256")).willReturn(signature);

                // when
                var response = webhookHandler.handle(httpRequest, "body");

                // then
                assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
                then(gitPull).should(never()).pull(any());
            }

            @ParameterizedTest
            @MethodSource("validSignatures")
            void handle_shouldProceed_whenSignatureValid(String validSignature) throws JsonProcessingException {
                // given
                given(httpHeaders.get("X-Hub-Signature-256")).willReturn(validSignature);
                var jsonNode = new ObjectMapper().createObjectNode().put("ref", "refs/heads/main");
                given(objectMapper.readTree("body")).willReturn(jsonNode);
                given(payloadHandler.extractBranchName(jsonNode)).willReturn(Optional.of("refs/heads/main"));
                given(gitProperties.getBranch()).willReturn("main");

                // when
                var response = webhookHandler.handle(httpRequest, "body");

                // then
                assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
                then(gitPull).should().pull(WEBHOOK);
            }

            static Stream<Arguments> validSignatures() {
                return Stream.of(
                        // HMAC-SHA256 of "body" with secret "my-secret"
                        Arguments.of("sha256=6af7ab0e299ab0bcb975868b8ad57a70025d90b4ae6607c76ad6fa7a7cfc6307"),
                        // HMAC-SHA1 of "body" with secret "my-secret"
                        Arguments.of("sha1=fb428df5ad2591eaa29f8c094a5d42d8dd65e6f9")
                );
            }
        }

        @Nested
        class WhenNoSecret {

            @BeforeEach
            void setUp() {
                webhookConfig.setSecret(null);
                given(httpHeaders.get("X-GitHub-Event")).willReturn("push");
            }

            @ParameterizedTest
            @MethodSource("matchingBranches")
            void handle_shouldTriggerPull_whenBranchMatches(String ref, String configuredBranch) throws JsonProcessingException {
                // given
                var jsonNode = new ObjectMapper().createObjectNode().put("ref", ref);
                given(objectMapper.readTree("body")).willReturn(jsonNode);
                given(payloadHandler.extractBranchName(jsonNode)).willReturn(Optional.of(ref));
                given(gitProperties.getBranch()).willReturn(configuredBranch);

                // when
                var response = webhookHandler.handle(httpRequest, "body");

                // then
                assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
                then(gitPull).should().pull(WEBHOOK);
            }

            static Stream<Arguments> matchingBranches() {
                return Stream.of(
                        Arguments.of("refs/heads/main", "main"),
                        Arguments.of("main", "main"),
                        Arguments.of("refs/heads/feature/my-feature", "feature/my-feature")
                );
            }

            @Test
            void handle_shouldReturnNotModified_whenBranchDoesNotMatch() throws JsonProcessingException {
                // given
                var jsonNode = new ObjectMapper().createObjectNode().put("ref", "refs/heads/develop");
                given(objectMapper.readTree("body")).willReturn(jsonNode);
                given(payloadHandler.extractBranchName(jsonNode)).willReturn(Optional.of("refs/heads/develop"));
                given(gitProperties.getBranch()).willReturn("main");

                // when
                var response = webhookHandler.handle(httpRequest, "body");

                // then
                assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NOT_MODIFIED.getCode());
                then(gitPull).should(never()).pull(any());
            }

            @Test
            void handle_shouldTriggerPull_whenHandlerReturnsEmptyBranch() throws JsonProcessingException {
                // given
                var jsonNode = new ObjectMapper().createObjectNode();
                given(objectMapper.readTree("body")).willReturn(jsonNode);
                given(payloadHandler.extractBranchName(jsonNode)).willReturn(Optional.empty());

                // when
                var response = webhookHandler.handle(httpRequest, "body");

                // then
                assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
                then(gitPull).should().pull(WEBHOOK);
            }

            @Test
            void handle_shouldReturnBadRequest_whenJsonParsingFails() throws JsonProcessingException {
                // given
                given(objectMapper.readTree("invalid-json")).willThrow(new JsonProcessingException("Parse error") {});

                // when
                var response = webhookHandler.handle(httpRequest, "invalid-json");

                // then
                assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
                then(gitPull).should(never()).pull(any());
            }
        }
    }
}
