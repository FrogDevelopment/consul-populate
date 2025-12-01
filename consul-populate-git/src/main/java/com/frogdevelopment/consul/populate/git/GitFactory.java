package com.frogdevelopment.consul.populate.git;

import lombok.extern.slf4j.Slf4j;

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

        log.debug("Cloning repository [{}]", gitProperties.getUri());
        return Git.cloneRepository()
                .setURI(gitProperties.getUri())
                .setDirectory(repositoryDirectory.toFile())
                .setGitDir(repositoryDirectory.resolve(Constants.DOT_GIT).toFile())
                .setBranch(gitProperties.getBranch())
                .setRemote(Constants.DEFAULT_REMOTE_NAME)
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

}
