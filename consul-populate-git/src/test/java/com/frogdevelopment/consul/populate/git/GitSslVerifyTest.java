package com.frogdevelopment.consul.populate.git;

import static com.frogdevelopment.consul.populate.git.pull.Status.FAILURE;
import static com.frogdevelopment.consul.populate.git.pull.Trigger.SCHEDULED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.frogdevelopment.consul.populate.PopulateService;
import com.frogdevelopment.consul.populate.git.pull.GitPull;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/**
 * Talks TLS for real: a local HTTPS server presents a self-signed certificate, which the JVM does not trust. The server
 * answers 404 to everything, so a request that reaches it is a connection that got through the TLS handshake.
 */
class GitSslVerifyTest {

    @TempDir
    static Path serverDir;

    @TempDir
    Path tempDir;

    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static HttpsServer server;
    private static String repositoryUrl;

    @BeforeAll
    static void startHttpsServerWithASelfSignedCertificate() throws Exception {
        final var keystore = serverDir.resolve("keystore.p12");
        final var keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        final var process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "test", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass", "changeit", "-keypass",
                "changeit")
                .redirectErrorStream(true)
                .start();
        final var output = new String(process.getInputStream().readAllBytes(), UTF_8);
        assertThat(process.waitFor()).as("keytool: %s", output).isZero();

        final var store = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(keystore)) {
            store.load(in, "changeit".toCharArray());
        }
        final var keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(store, "changeit".toCharArray());
        final var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers.getKeyManagers(), null, null);

        server = HttpsServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        server.createContext("/", exchange -> {
            REQUESTS.incrementAndGet();
            final var body = "not found".getBytes(UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        repositoryUrl = "https://127.0.0.1:" + server.getAddress().getPort() + "/org/repo.git";
    }

    @AfterAll
    static void stopHttpsServer() {
        server.stop(0);
    }

    @BeforeEach
    void resetRequestCounter() {
        REQUESTS.set(0);
    }

    @Test
    void clone_should_reject_the_self_signed_certificate_by_default() {
        // when
        final var thrown = catchThrowable(() -> clone(true));

        // then - the handshake fails, so nothing reaches the server
        assertThat(thrown).isInstanceOf(GitAPIException.class);
        assertThat(causesOf(thrown)).anyMatch(SSLException.class::isInstance);
        assertThat(REQUESTS).hasValue(0);
    }

    @Test
    void clone_should_accept_the_self_signed_certificate_when_ssl_verify_is_off() {
        // when
        final var thrown = catchThrowable(() -> clone(false));

        // then - the TLS handshake went through; the failure is the server's 404
        assertThat(thrown).isInstanceOf(GitAPIException.class);
        assertThat(causesOf(thrown)).noneMatch(SSLException.class::isInstance);
        assertThat(REQUESTS.get()).isPositive();
    }

    @Test
    void pull_should_reject_the_self_signed_certificate_by_default() throws Exception {
        try (var git = repositoryWithOrigin()) {
            // when
            final var status = new GitPull(git, properties(true), mock(PopulateService.class)).pull(SCHEDULED);

            // then
            assertThat(status).isEqualTo(FAILURE);
            assertThat(REQUESTS).hasValue(0);
        }
    }

    @Test
    void pull_should_accept_the_self_signed_certificate_when_ssl_verify_is_off() throws Exception {
        try (var git = repositoryWithOrigin()) {
            // when - the pull still fails, on the server's 404, but only after the TLS handshake
            final var status = new GitPull(git, properties(false), mock(PopulateService.class)).pull(SCHEDULED);

            // then
            assertThat(status).isEqualTo(FAILURE);
            assertThat(REQUESTS.get()).isPositive();
        }
    }

    private void clone(final boolean sslVerify) throws GitAPIException {
        final var directoryProvider = mock(RepositoryDirectoryProvider.class);
        given(directoryProvider.getRepository()).willReturn(tempDir.resolve("clone"));

        new GitFactory().git(properties(sslVerify), directoryProvider);
    }

    private Git repositoryWithOrigin() throws Exception {
        final var git = Git.init().setDirectory(tempDir.resolve("local").toFile()).call();
        final var config = git.getRepository().getConfig();
        config.setString("remote", Constants.DEFAULT_REMOTE_NAME, "url", repositoryUrl);
        config.setString("remote", Constants.DEFAULT_REMOTE_NAME, "fetch", "+refs/heads/*:refs/remotes/origin/*");
        config.save();
        return git;
    }

    private static GitProperties properties(final boolean sslVerify) {
        final var properties = new GitProperties();
        properties.setUri(repositoryUrl);
        properties.setBranch("main");
        properties.setSslVerify(sslVerify);
        return properties;
    }

    private static List<Throwable> causesOf(final Throwable throwable) {
        final var causes = new ArrayList<Throwable>();
        for (var cause = throwable; cause != null && !causes.contains(cause); cause = cause.getCause()) {
            causes.add(cause);
        }
        return causes;
    }
}
