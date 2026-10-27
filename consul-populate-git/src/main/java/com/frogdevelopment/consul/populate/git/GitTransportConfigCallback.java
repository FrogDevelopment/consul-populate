package com.frogdevelopment.consul.populate.git;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Objects;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;

import io.micronaut.core.util.StringUtils;

/**
 * Applies the credentials and {@code consul.git.ssl-verify} to the transports of the clone and of the fetch.
 *
 * <p>The credentials are given to each transport instead of being set as the default of the JVM
 * ({@code CredentialsProvider.setDefault}), which any other use of JGit in the same process would inherit.
 *
 * <p>JGit reads {@code http.sslVerify} from the configuration of the repository, which does not exist yet for the
 * initial clone, so it cannot be driven by configuration alone. When the verification is turned off, the HTTP transport
 * gets a connection factory that tells JGit's own trust-all implementation to skip the certificate and host name checks,
 * whatever the repository configuration says. Other transports (such as SSH) are left alone.
 *
 * @author Le Gall Benoît
 * @since 1.4.0
 */
public final class GitTransportConfigCallback implements TransportConfigCallback {

    private final boolean sslVerify;
    private final CredentialsProvider credentialsProvider;

    /**
     * @param properties where the credentials come from (a token, or a user name with an optional password) and whether
     *                   the TLS certificate of the git server is verified
     */
    public GitTransportConfigCallback(final GitProperties properties) {
        this.sslVerify = properties.isSslVerify();
        this.credentialsProvider = credentialsOf(properties);
    }

    /**
     * @return {@code true} when no credentials are configured: a public repository is accessed anonymously
     */
    public boolean isAnonymous() {
        return credentialsProvider == null;
    }

    @Override
    public void configure(final Transport transport) {
        if (credentialsProvider != null) {
            transport.setCredentialsProvider(credentialsProvider);
        }
        if (!sslVerify && transport instanceof TransportHttp http) {
            http.setHttpConnectionFactory(new TrustAllConnectionFactory());
        }
    }

    private static CredentialsProvider credentialsOf(final GitProperties properties) {
        final var token = properties.getToken();
        if (StringUtils.isNotEmpty(token)) {
            return new UsernamePasswordCredentialsProvider(token, "");
        }

        final var username = properties.getUsername();
        if (StringUtils.isNotEmpty(username)) {
            // JGit's (String, String) constructor rejects a null password
            return new UsernamePasswordCredentialsProvider(username, Objects.requireNonNullElse(properties.getPassword(), ""));
        }
        return null;
    }

    /**
     * JGit's default connection factory, with the SSL verification forced off.
     */
    private static final class TrustAllConnectionFactory extends JDKHttpConnectionFactory {

        @Override
        public GitSession newSession() {
            final var delegate = super.newSession();
            return new GitSession() {
                @Override
                public HttpConnection configure(final HttpConnection connection, final boolean ignoredSslVerify)
                        throws IOException, GeneralSecurityException {
                    return delegate.configure(connection, false);
                }

                @Override
                public void close() {
                    delegate.close();
                }
            };
        }
    }
}
