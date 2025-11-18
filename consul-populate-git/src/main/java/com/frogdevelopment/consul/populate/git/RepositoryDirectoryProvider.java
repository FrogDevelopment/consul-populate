package com.frogdevelopment.consul.populate.git;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Singleton;

import org.eclipse.jgit.transport.URIish;

/**
 * Thread-safe provider for the git repository directory path.
 * Uses lazy initialization with memoization to create the directory only once.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
@Singleton
@RequiredArgsConstructor
public class RepositoryDirectoryProvider {

    private final GitProperties gitProperties;

    private final AtomicReference<Path> repository = new AtomicReference<>();

    /**
     * Returns the git repository directory path, creating it lazily on first access.
     * Thread-safe and guaranteed to create the directory only once.
     *
     * @return the repository directory path
     */
    public Path getRepository() {
        var result = repository.get();
        if (result == null) {
            result = createRepositoryDirectory();
            if (!repository.compareAndSet(null, result)) {
                // Another thread initialized it first, use that value
                result = repository.get();
            }
        }
        return result;
    }

    private Path createRepositoryDirectory() {
        try {
            final var localPath = gitProperties.getLocalPath();
            if (Files.notExists(localPath)) {
                Files.createDirectories(localPath);
            }
            final var tmpDirectory = Files.createTempDirectory(localPath, "consul-populate-");

            final var urIish = new URIish(gitProperties.getUri());

            return tmpDirectory.resolve(urIish.getHumanishName());
        } catch (final IOException | URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
