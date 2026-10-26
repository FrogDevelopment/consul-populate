package com.frogdevelopment.consul.populate.git;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;

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
        createCredentialsProvider(gitProperties).ifPresent(CredentialsProvider::setDefault);
        final var repositoryDirectory = repositoryDirectoryProvider.getRepository();

        final var sslVerify = gitProperties.isSslVerify();
        if (!sslVerify) {
            log.warn("consul.git.ssl-verify=false: the TLS certificate of the git server is NOT verified, so the "
                    + "connection can be intercepted. Prefer adding the certificate to the JVM trust store.");
        }

        log.debug("Cloning repository [{}]", gitProperties.getUri());
        return Git.cloneRepository()
                .setURI(gitProperties.getUri())
                .setDirectory(repositoryDirectory.toFile())
                .setGitDir(repositoryDirectory.resolve(Constants.DOT_GIT).toFile())
                .setBranch(gitProperties.getBranch())
                .setRemote(Constants.DEFAULT_REMOTE_NAME)
                .setTransportConfigCallback(new GitTransportConfigCallback(sslVerify))
                .call();
    }

    /**
     * @return the credentials to use, or empty to clone anonymously (public repository)
     */
    private static Optional<CredentialsProvider> createCredentialsProvider(final GitProperties gitProperties) {
        final var token = gitProperties.getToken();
        if (StringUtils.isNotEmpty(token)) {
            return Optional.of(new UsernamePasswordCredentialsProvider(token, ""));
        }

        final var username = gitProperties.getUsername();
        if (StringUtils.isNotEmpty(username)) {
            // JGit's (String, String) constructor rejects a null password
            return Optional.of(new UsernamePasswordCredentialsProvider(
                    username, Objects.requireNonNullElse(gitProperties.getPassword(), "")));
        }

        log.info("No credentials configured, cloning anonymously");
        return Optional.empty();
    }

}
