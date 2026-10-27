package com.frogdevelopment.consul.populate.git;

import static com.frogdevelopment.consul.populate.git.pull.Trigger.SCHEDULED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.Base64;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.frogdevelopment.consul.populate.PopulateService;
import com.frogdevelopment.consul.populate.git.pull.GitPull;

/**
 * What JGit really sends: the server demands Basic authentication, as a git server does, and records what it gets. The
 * TLS check is off because its certificate is self-signed (see {@link GitSslVerifyTest}).
 */
class GitCredentialsTest {

    @TempDir
    static Path serverDir;

    @TempDir
    Path tempDir;

    private static SelfSignedHttpsServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = SelfSignedHttpsServer.start(serverDir, true);
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void reset() {
        server.reset();
    }

    @Test
    void clone_shouldSendTheToken_withoutSettingTheDefaultCredentialsOfTheJvm() {
        // given
        final var before = CredentialsProvider.getDefault();
        final var properties = properties();
        properties.setToken("my-token");

        // when
        catchThrowable(() -> clone(properties));

        // then - sent after the 401 challenge, as the user name with an empty password
        assertThat(server.authorizations()).contains(basic("my-token:"));
        assertThat(CredentialsProvider.getDefault()).isSameAs(before);
    }

    @Test
    void clone_shouldSendTheUserNameAndPassword() {
        // given
        final var properties = properties();
        properties.setUsername("user");
        properties.setPassword("pass");

        // when
        catchThrowable(() -> clone(properties));

        // then
        assertThat(server.authorizations()).contains(basic("user:pass"));
    }

    @Test
    void clone_shouldNotSendAnything_whenNoCredentialsAreConfigured() {
        // when - anonymous: the server demands credentials, which fails
        final var thrown = catchThrowable(() -> clone(properties()));

        // then
        assertThat(thrown).isNotNull();
        assertThat(server.authorizations()).containsOnly("");
    }

    @Test
    void pull_shouldSendTheCredentialsToo_withoutAnyDefaultCredentials() throws Exception {
        // given - a fetch has no default to fall back on: it gets its own credentials
        final var before = CredentialsProvider.getDefault();
        final var properties = properties();
        properties.setToken("fetch-token");
        try (var local = Git.init().setDirectory(tempDir.resolve("local").toFile()).call()) {
            final var config = local.getRepository().getConfig();
            config.setString("remote", Constants.DEFAULT_REMOTE_NAME, "url", server.repositoryUrl());
            config.setString("remote", Constants.DEFAULT_REMOTE_NAME, "fetch", "+refs/heads/*:refs/remotes/origin/*");
            config.save();

            // when
            new GitPull(local, properties, mock(PopulateService.class)).pull(SCHEDULED);
        }

        // then
        assertThat(server.authorizations()).contains(basic("fetch-token:"));
        assertThat(CredentialsProvider.getDefault()).isSameAs(before);
    }

    private void clone(final GitProperties properties) throws Exception {
        final var directoryProvider = mock(RepositoryDirectoryProvider.class);
        given(directoryProvider.getRepository()).willReturn(tempDir.resolve("clone"));
        new GitFactory().git(properties, directoryProvider);
    }

    private static GitProperties properties() {
        final var properties = new GitProperties();
        properties.setUri(server.repositoryUrl());
        properties.setBranch("main");
        properties.setSslVerify(false);
        return properties;
    }

    private static String basic(final String userAndPassword) {
        return "Basic " + Base64.getEncoder().encodeToString(userAndPassword.getBytes(UTF_8));
    }
}
