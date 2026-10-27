package com.frogdevelopment.consul.populate.git;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/**
 * A local HTTPS server with a self-signed certificate (made by {@code keytool}, which the JVM does not trust) that
 * answers 404 to everything, so that a request that reaches it is a connection that got through the TLS handshake. It
 * records the {@code Authorization} header of each request and, when asked to, demands Basic authentication first, the
 * way a git server does.
 */
final class SelfSignedHttpsServer implements AutoCloseable {

    private final HttpsServer server;
    private final List<String> authorizations = new CopyOnWriteArrayList<>();

    private SelfSignedHttpsServer(final HttpsServer server) {
        this.server = server;
    }

    /**
     * @param directory         where the keystore is written
     * @param demandCredentials {@code true} to answer 401 with a Basic challenge until a request has credentials
     */
    static SelfSignedHttpsServer start(final Path directory, final boolean demandCredentials) throws Exception {
        final var keystore = directory.resolve("keystore.p12");
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

        final var httpsServer = HttpsServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        final var result = new SelfSignedHttpsServer(httpsServer);
        httpsServer.createContext("/", exchange -> {
            final var authorization = exchange.getRequestHeaders().getFirst("Authorization");
            result.authorizations.add(authorization == null ? "" : authorization);
            if (demandCredentials && authorization == null) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"git\"");
                exchange.sendResponseHeaders(401, -1);
            } else {
                final var body = "not found".getBytes(UTF_8);
                exchange.sendResponseHeaders(404, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        httpsServer.start();
        return result;
    }

    String repositoryUrl() {
        return "https://127.0.0.1:" + server.getAddress().getPort() + "/org/repo.git";
    }

    /** The {@code Authorization} header of every request received, an empty string when there was none. */
    List<String> authorizations() {
        return authorizations;
    }

    int requests() {
        return authorizations.size();
    }

    void reset() {
        authorizations.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
