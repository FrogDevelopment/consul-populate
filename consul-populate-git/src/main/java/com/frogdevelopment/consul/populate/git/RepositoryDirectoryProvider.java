package com.frogdevelopment.consul.populate.git;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
@Slf4j
@Singleton
@RequiredArgsConstructor
public class RepositoryDirectoryProvider {

    private final GitProperties gitProperties;

    /** The temporary directory created for this run, and the clone inside it. */
    private record Location(Path workingDirectory, Path repository) {
    }

    private final AtomicReference<Location> location = new AtomicReference<>();

    /**
     * Returns the git repository directory path, creating it lazily on first access.
     * Thread-safe and guaranteed to create the directory only once: a thread that loses the race removes the temporary
     * directory it created instead of leaving it behind.
     *
     * @return the repository directory path
     */
    public Path getRepository() {
        var result = location.get();
        if (result == null) {
            final var created = createRepositoryDirectory();
            if (location.compareAndSet(null, created)) {
                result = created;
            } else {
                // Another thread initialized it first, use that value
                deleteEmptyDirectory(created.workingDirectory());
                result = location.get();
            }
        }
        return result.repository();
    }

    /**
     * Returns the temporary directory that holds the clone, which is what has to be deleted to leave nothing behind.
     *
     * @return the directory, or empty if the repository was never requested (so nothing was created)
     */
    public Optional<Path> getWorkingDirectory() {
        return Optional.ofNullable(location.get()).map(Location::workingDirectory);
    }

    private static void deleteEmptyDirectory(final Path directory) {
        try {
            Files.deleteIfExists(directory);
        } catch (final IOException e) {
            log.warn("Unable to remove the unused temporary directory {}", directory, e);
        }
    }

    private Location createRepositoryDirectory() {
        try {
            final var localPath = gitProperties.getLocalPath();
            if (Files.notExists(localPath)) {
                Files.createDirectories(localPath);
            }
            final var tmpDirectory = Files.createTempDirectory(localPath, "consul-populate-");

            final var urIish = new URIish(gitProperties.getUri());

            return new Location(tmpDirectory, tmpDirectory.resolve(urIish.getHumanishName()));
        } catch (final IOException | URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
