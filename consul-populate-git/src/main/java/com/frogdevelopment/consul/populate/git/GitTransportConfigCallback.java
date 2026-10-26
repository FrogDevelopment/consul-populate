package com.frogdevelopment.consul.populate.git;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;

/**
 * Applies {@code consul.git.ssl-verify} to the transports of the clone and of the fetch.
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

    /**
     * @param sslVerify {@code false} to accept any TLS certificate of the git server
     */
    public GitTransportConfigCallback(final boolean sslVerify) {
        this.sslVerify = sslVerify;
    }

    @Override
    public void configure(final Transport transport) {
        if (!sslVerify && transport instanceof TransportHttp http) {
            http.setHttpConnectionFactory(new TrustAllConnectionFactory());
        }
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
