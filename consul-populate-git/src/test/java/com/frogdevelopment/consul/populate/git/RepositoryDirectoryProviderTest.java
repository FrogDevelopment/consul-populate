package com.frogdevelopment.consul.populate.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.nio.file.Path;

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
