package com.frogdevelopment.consul.populate.git;

import lombok.extern.slf4j.Slf4j;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import jakarta.inject.Singleton;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.StringUtils;

/**
 * Factory for creating and configuring the {@link Git} instance.
 * Clones the repository on bean creation and automatically closes it on shutdown.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
@Slf4j
@Factory
public class GitFactory {

    /**
     * Creates a Git instance by cloning the configured repository.
     * The instance is automatically closed on application shutdown via {@link Bean#preDestroy()}.
     *
     * @param gitProperties git configuration properties
     * @param repositoryDirectoryProvider provides the target directory for cloning
     * @return configured Git instance connected to the cloned repository
     * @throws GitAPIException if cloning fails
     */
    @Singleton
    @Bean(preDestroy = "close")
    public Git git(final GitProperties gitProperties,
                   final RepositoryDirectoryProvider repositoryDirectoryProvider) throws GitAPIException {
        final var credentialsProvider = createCredentialsProvider(gitProperties);
        CredentialsProvider.setDefault(credentialsProvider);
        final var repositoryDirectory = repositoryDirectoryProvider.getRepository();

        // Configure SSL verification
        if (!gitProperties.isSslVerify()) {
            log.warn("SSL verification is disabled for git repository [{}]. This is insecure and should only be used in development!",
                    gitProperties.getUri());
            configureInsecureSSL();
        }

        log.debug("Cloning repository [{}]", gitProperties.getUri());
        return Git.cloneRepository()
                .setURI(gitProperties.getUri())
                .setDirectory(repositoryDirectory.toFile())
                .setGitDir(repositoryDirectory.resolve(Constants.DOT_GIT).toFile())
                .setBranch(gitProperties.getBranch())
                .setRemote(Constants.DEFAULT_REMOTE_NAME)
//                .setCredentialsProvider(credentialsProvider)
                .call();
    }

    private static CredentialsProvider createCredentialsProvider(final GitProperties gitProperties) {
        final var token = gitProperties.getToken();
        if (StringUtils.isNotEmpty(token)) {
            return new UsernamePasswordCredentialsProvider(token, "");
        } else {
            return new UsernamePasswordCredentialsProvider(gitProperties.getUsername(), gitProperties.getPassword());
        }
    }

    /**
     * Configures JGit to skip SSL certificate verification.
     * This creates a trust manager that accepts all certificates and installs it as the default.
     * <p>
     * WARNING: This is insecure and should only be used in development/testing environments
     * with self-signed certificates or internal CAs.
     */
    private static void configureInsecureSSL() {
        try {
            // Create a trust manager that accepts all certificates
            final var trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            // Install the all-trusting trust manager
            final var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Set default SSL context for HTTPS connections
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("Failed to configure insecure SSL", e);
            throw new RuntimeException("Failed to configure SSL for git operations", e);
        }
    }

}
