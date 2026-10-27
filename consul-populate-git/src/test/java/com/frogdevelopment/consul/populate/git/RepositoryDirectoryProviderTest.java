package com.frogdevelopment.consul.populate.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepositoryDirectoryProviderTest {

    @Mock
    private GitProperties gitProperties;

    @InjectMocks
    private RepositoryDirectoryProvider provider;

    @TempDir
    private Path tempDir;

    @Test
    void shouldCreateDirectoryOnFirstAccess() {
        // given
        given(gitProperties.getLocalPath()).willReturn(tempDir);
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");

        // when
        var result = provider.getRepository();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getFileName()).hasToString("repo");
        assertThat(result.getParent()).hasParentRaw(tempDir);
    }

    @Test
    void shouldNotLeaveTemporaryDirectoriesBehind_whenSeveralThreadsAskAtTheSameTime() throws Exception {
        // given
        given(gitProperties.getLocalPath()).willReturn(tempDir);
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
        final var threads = 32;
        final var start = new CountDownLatch(1);

        // when - every thread creates its directory before it knows whether it won the race
        final var paths = new ArrayList<Path>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            final var calls = new ArrayList<Future<Path>>();
            for (var i = 0; i < threads; i++) {
                calls.add(executor.submit(() -> {
                    start.await();
                    return provider.getRepository();
                }));
            }
            start.countDown();
            for (final var call : calls) {
                paths.add(call.get(30, TimeUnit.SECONDS));
            }
        }

        // then - one repository for everybody, and the temporary directories of the losers are gone
        assertThat(paths).containsOnly(paths.get(0));
        try (var children = Files.list(tempDir)) {
            assertThat(children.filter(path -> path.getFileName().toString().startsWith("consul-populate-")))
                    .containsExactly(paths.get(0).getParent());
        }
    }

    @Test
    void shouldExposeTheWorkingDirectoryOnlyOnceTheRepositoryWasRequested() {
        // given
        given(gitProperties.getLocalPath()).willReturn(tempDir);
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");

        // when / then - before the first request nothing exists, so there is nothing to delete
        assertThat(provider.getWorkingDirectory()).isEmpty();
        var repository = provider.getRepository();
        assertThat(provider.getWorkingDirectory()).contains(repository.getParent());
    }

    @Test
    void shouldReturnSameInstanceOnSubsequentCalls() {
        // given
        given(gitProperties.getLocalPath()).willReturn(tempDir);
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");

        // when
        var firstCall = provider.getRepository();
        var secondCall = provider.getRepository();
        var thirdCall = provider.getRepository();

        // then
        assertThat(firstCall)
                .isSameAs(secondCall)
                .isSameAs(thirdCall);
        then(gitProperties).should(times(1)).getLocalPath();
        then(gitProperties).should(times(1)).getUri();
    }

    @Test
    void shouldCreateParentDirectories_whenLocalPathDoesNotExist() {
        // given
        var nonExistentPath = tempDir.resolve("nested/deeply/nested/path");
        given(gitProperties.getLocalPath()).willReturn(nonExistentPath);
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");

        // when
        var result = provider.getRepository();

        // then
        assertThat(result).isNotNull();
        assertThat(nonExistentPath).exists();
    }

    @ParameterizedTest
    @CsvSource({
            "https://github.com/organization/my-awesome-repo.git,my-awesome-repo", // From URI
            "https://github.com/user/repo,repo", // From Uri Without Git Suffix
            "git@github.com:user/repo.git,repo" // From Ssh Uri
    })
    void shouldExtractHumanishName(final String uri, final String fileName) {
        // given
        given(gitProperties.getLocalPath()).willReturn(tempDir);
        given(gitProperties.getUri()).willReturn(uri);

        // when
        var result = provider.getRepository();

        // then
        assertThat(result.getFileName()).hasToString(fileName);
    }

    @Test
    void shouldThrowException_whenUriIsInvalid() {
        // given
        given(gitProperties.getLocalPath()).willReturn(tempDir);
        given(gitProperties.getUri()).willReturn("");

        // when/then
        assertThatThrownBy(() -> provider.getRepository())
                .isInstanceOf(IllegalArgumentException.class)
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    void shouldCreateTempDirectoryInsideLocalPath() {
        // given
        given(gitProperties.getLocalPath()).willReturn(tempDir);
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");

        // when
        var result = provider.getRepository();

        // then
        assertThat(result.getParent()).hasParentRaw(tempDir);
        assertThat(result.getParent().getFileName().toString()).startsWith("consul-populate-");
    }
}
