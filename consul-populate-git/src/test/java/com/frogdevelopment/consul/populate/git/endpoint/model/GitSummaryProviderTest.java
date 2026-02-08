package com.frogdevelopment.consul.populate.git.endpoint.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.frogdevelopment.consul.populate.files.ImportFileProperties;
import com.frogdevelopment.consul.populate.git.GitProperties;
import com.frogdevelopment.consul.populate.git.RepositoryDirectoryProvider;
import com.frogdevelopment.consul.populate.git.pull.GitPull;
import com.frogdevelopment.consul.populate.git.pull.Trigger;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GitSummaryProviderTest {

    @Mock
    private Git git;
    @Mock
    private GitPull gitPull;
    @Mock
    private GitProperties gitProperties;
    @Mock
    private RepositoryDirectoryProvider repositoryDirectoryProvider;

    @InjectMocks
    private GitSummaryProvider gitSummaryProvider;

    @Nested
    class GenerateSummary {

        @Test
        void shouldReturnSummaryWithFileProperties() throws Exception {
            // given
            var fileProperties = new ImportFileProperties();
            given(gitProperties.getFileProperties()).willReturn(fileProperties);
            given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.isPollEnabled()).willReturn(false);

            var repository = mock(Repository.class);
            given(git.getRepository()).willReturn(repository);
            given(repository.resolve("HEAD")).willReturn(null);

            var statusCommand = mock(StatusCommand.class);
            var status = mock(Status.class);
            given(git.status()).willReturn(statusCommand);
            given(statusCommand.call()).willReturn(status);
            given(status.isClean()).willReturn(true);

            // when
            var result = gitSummaryProvider.generateSummary();

            // then
            assertThat(result.files()).isSameAs(fileProperties);
        }

        @Test
        void shouldReturnSummaryWithRepoInfo() throws Exception {
            // given
            given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.isPollEnabled()).willReturn(false);
            given(gitProperties.getFileProperties()).willReturn(new ImportFileProperties());

            var repository = mock(Repository.class);
            given(git.getRepository()).willReturn(repository);
            given(repository.resolve("HEAD")).willReturn(null);

            var statusCommand = mock(StatusCommand.class);
            var status = mock(Status.class);
            given(git.status()).willReturn(statusCommand);
            given(statusCommand.call()).willReturn(status);
            given(status.isClean()).willReturn(true);

            // when
            var result = gitSummaryProvider.generateSummary();

            // then
            assertThat(result.repo().uri()).isEqualTo("https://github.com/user/repo.git");
            assertThat(result.repo().branch()).isEqualTo("main");
            assertThat(result.repo().localPath()).isEqualTo("/tmp/repo");
        }

        @Test
        void shouldMaskCredentialsInUri() throws Exception {
            // given
            given(gitProperties.getUri()).willReturn("https://user:password@github.com/user/repo.git");
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.isPollEnabled()).willReturn(false);
            given(gitProperties.getFileProperties()).willReturn(new ImportFileProperties());

            var repository = mock(Repository.class);
            given(git.getRepository()).willReturn(repository);
            given(repository.resolve("HEAD")).willReturn(null);

            var statusCommand = mock(StatusCommand.class);
            var status = mock(Status.class);
            given(git.status()).willReturn(statusCommand);
            given(statusCommand.call()).willReturn(status);
            given(status.isClean()).willReturn(true);

            // when
            var result = gitSummaryProvider.generateSummary();

            // then
            assertThat(result.repo().uri()).isEqualTo("https://github.com/user/repo.git");
        }

        @Test
        void shouldReturnDirtyFalse_whenRepoIsClean() throws Exception {
            // given
            given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.isPollEnabled()).willReturn(false);
            given(gitProperties.getFileProperties()).willReturn(new ImportFileProperties());

            var repository = mock(Repository.class);
            given(git.getRepository()).willReturn(repository);
            given(repository.resolve("HEAD")).willReturn(null);

            var statusCommand = mock(StatusCommand.class);
            var status = mock(Status.class);
            given(git.status()).willReturn(statusCommand);
            given(statusCommand.call()).willReturn(status);
            given(status.isClean()).willReturn(true);

            // when
            var result = gitSummaryProvider.generateSummary();

            // then
            assertThat(result.repo().dirty()).isFalse();
        }

        @Test
        void shouldReturnDirtyTrue_whenRepoHasUncommittedChanges() throws Exception {
            // given
            given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.isPollEnabled()).willReturn(false);
            given(gitProperties.getFileProperties()).willReturn(new ImportFileProperties());

            var objectReader = mock(ObjectReader.class);
            var repository = mock(Repository.class);
            given(git.getRepository()).willReturn(repository);
            given(repository.resolve("HEAD")).willReturn(null);
            given(repository.newObjectReader()).willReturn(objectReader);

            var statusCommand = mock(StatusCommand.class);
            var status = mock(Status.class);
            given(git.status()).willReturn(statusCommand);
            given(statusCommand.call()).willReturn(status);
            given(status.isClean()).willReturn(false);

            // when
            var result = gitSummaryProvider.generateSummary();

            // then
            assertThat(result.repo().dirty()).isTrue();
        }

        @Test
        void shouldReturnNullHead_whenNoCommitsExist() throws Exception {
            // given
            given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.isPollEnabled()).willReturn(false);
            given(gitProperties.getFileProperties()).willReturn(new ImportFileProperties());

            var repository = mock(Repository.class);
            given(git.getRepository()).willReturn(repository);
            given(repository.resolve("HEAD")).willReturn(null);

            var statusCommand = mock(StatusCommand.class);
            var status = mock(Status.class);
            given(git.status()).willReturn(statusCommand);
            given(statusCommand.call()).willReturn(status);
            given(status.isClean()).willReturn(true);

            // when
            var result = gitSummaryProvider.generateSummary();

            // then
            assertThat(result.repo().head()).isNull();
        }

        @Test
        void shouldHandleRepoErrorGracefully() {
            // given
            given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.isPollEnabled()).willReturn(false);
            given(gitProperties.getFileProperties()).willReturn(new ImportFileProperties());

            given(git.getRepository()).willThrow(new RuntimeException("Repository error"));

            // when
            var result = gitSummaryProvider.generateSummary();

            // then
            assertThat(result.repo().head()).isNull();
            assertThat(result.repo().dirty()).isFalse();
        }

        @Test
        void shouldMaskUsernameOnlyInUri() throws Exception {
            // given
            given(gitProperties.getUri()).willReturn("https://username@github.com/user/repo.git");
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.isPollEnabled()).willReturn(false);
            given(gitProperties.getFileProperties()).willReturn(new ImportFileProperties());

            var repository = mock(Repository.class);
            given(git.getRepository()).willReturn(repository);
            given(repository.resolve("HEAD")).willReturn(null);

            var statusCommand = mock(StatusCommand.class);
            var status = mock(Status.class);
            given(git.status()).willReturn(statusCommand);
            given(statusCommand.call()).willReturn(status);
            given(status.isClean()).willReturn(true);

            // when
            var result = gitSummaryProvider.generateSummary();

            // then
            assertThat(result.repo().uri()).isEqualTo("https://github.com/user/repo.git");
        }

        @Test
        void shouldPopulateHeadInfo_whenCommitExists() throws Exception {
            // given
            given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.isPollEnabled()).willReturn(false);
            given(gitProperties.getFileProperties()).willReturn(new ImportFileProperties());

            var repository = mock(org.eclipse.jgit.lib.Repository.class);
            var objectId = org.eclipse.jgit.lib.ObjectId.fromString("a1b2c3d4e5f6789012345678901234567890abcd");
            var revCommit = mock(org.eclipse.jgit.revwalk.RevCommit.class);
            var personIdent = mock(org.eclipse.jgit.lib.PersonIdent.class);

            given(git.getRepository()).willReturn(repository);
            given(repository.resolve("HEAD")).willReturn(objectId);
            given(repository.newObjectReader()).willReturn(mock(org.eclipse.jgit.lib.ObjectReader.class));

            given(revCommit.getId()).willReturn(objectId);
            given(revCommit.getShortMessage()).willReturn("Fix authentication bug");
            given(revCommit.getAuthorIdent()).willReturn(personIdent);
            given(personIdent.getWhenAsInstant()).willReturn(java.time.Instant.parse("2024-01-15T10:30:00Z"));

            var statusCommand = mock(StatusCommand.class);
            var status = mock(Status.class);
            given(git.status()).willReturn(statusCommand);
            given(statusCommand.call()).willReturn(status);
            given(status.isClean()).willReturn(true);

            try (var ignored = org.mockito.Mockito.mockConstruction(org.eclipse.jgit.revwalk.RevWalk.class,
                    (mock, context) -> given(mock.parseCommit(objectId)).willReturn(revCommit))) {

                // when
                var result = gitSummaryProvider.generateSummary();

                // then
                assertThat(result.repo().head()).isNotNull();
                assertThat(result.repo().head().id()).isEqualTo("a1b2c3d4e5f6789012345678901234567890abcd");
                assertThat(result.repo().head().shortId()).isEqualTo("a1b2c3d");
                assertThat(result.repo().head().message()).isEqualTo("Fix authentication bug");
                assertThat(result.repo().head().time()).isEqualTo("2024-01-15T10:30:00Z");
            }
        }

        @Test
        void shouldKeepOriginalUri_whenUriMaskingFails() throws Exception {
            // given
            var invalidUri = "not a valid uri with spaces";
            given(gitProperties.getUri()).willReturn(invalidUri);
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.isPollEnabled()).willReturn(false);
            given(gitProperties.getFileProperties()).willReturn(new ImportFileProperties());

            var repository = mock(Repository.class);
            given(git.getRepository()).willReturn(repository);
            given(repository.resolve("HEAD")).willReturn(null);

            var statusCommand = mock(StatusCommand.class);
            var status = mock(Status.class);
            given(git.status()).willReturn(statusCommand);
            given(statusCommand.call()).willReturn(status);
            given(status.isClean()).willReturn(true);

            // when
            var result = gitSummaryProvider.generateSummary();

            // then - original URI kept when masking fails
            assertThat(result.repo().uri()).isEqualTo(invalidUri);
        }
    }

    @Nested
    class PullSummary {

        @Test
        void shouldReturnPullSummary_whenPollingEnabled() throws Exception {
            // given
            given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.getFileProperties()).willReturn(new ImportFileProperties());

            var repository = mock(Repository.class);
            given(git.getRepository()).willReturn(repository);
            given(repository.resolve("HEAD")).willReturn(null);

            var statusCommand = mock(StatusCommand.class);
            var status = mock(Status.class);
            given(git.status()).willReturn(statusCommand);
            given(statusCommand.call()).willReturn(status);
            given(status.isClean()).willReturn(true);

            given(gitProperties.isPollEnabled()).willReturn(true);
            given(gitProperties.getPollInterval()).willReturn(Duration.ofMinutes(5));
            given(gitPull.getTrigger()).willReturn(Trigger.SCHEDULED);
            given(gitPull.getLastPullTime()).willReturn("2024-01-15T10:30:00Z");
            given(gitPull.getLastPullDuration()).willReturn("00.123s");
            given(gitPull.getLastPullOutcome()).willReturn(com.frogdevelopment.consul.populate.git.pull.Status.SUCCESS);

            // when
            var result = gitSummaryProvider.generateSummary();

            // then
            assertThat(result.pull().scheduled()).isTrue();
            assertThat(result.pull().scheduledInterval()).isEqualTo("PT5M");
            assertThat(result.pull().trigger()).isEqualTo(Trigger.SCHEDULED);
            assertThat(result.pull().lastPullTime()).isEqualTo("2024-01-15T10:30:00Z");
            assertThat(result.pull().lastPullDuration()).isEqualTo("00.123s");
            assertThat(result.pull().lastPullOutcome()).isEqualTo(com.frogdevelopment.consul.populate.git.pull.Status.SUCCESS);
        }

        @Test
        void shouldReturnNullInterval_whenPollingDisabled() throws Exception {
            // given
            given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.getFileProperties()).willReturn(new ImportFileProperties());

            var repository = mock(Repository.class);
            given(git.getRepository()).willReturn(repository);
            given(repository.resolve("HEAD")).willReturn(null);

            var statusCommand = mock(StatusCommand.class);
            var status = mock(Status.class);
            given(git.status()).willReturn(statusCommand);
            given(statusCommand.call()).willReturn(status);
            given(status.isClean()).willReturn(true);

            given(gitProperties.isPollEnabled()).willReturn(false);
            given(gitPull.getTrigger()).willReturn(Trigger.FORCED);

            // when
            var result = gitSummaryProvider.generateSummary();

            // then
            assertThat(result.pull().scheduled()).isFalse();
            assertThat(result.pull().scheduledInterval()).isNull();
        }

        @Test
        void shouldReturnNullPullInfo_whenNoPullHasOccurred() throws Exception {
            // given
            given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
            given(gitProperties.getBranch()).willReturn("main");
            given(repositoryDirectoryProvider.getRepository()).willReturn(Path.of("/tmp/repo"));
            given(gitProperties.getFileProperties()).willReturn(new ImportFileProperties());

            var repository = mock(Repository.class);
            given(git.getRepository()).willReturn(repository);
            given(repository.resolve("HEAD")).willReturn(null);

            var statusCommand = mock(StatusCommand.class);
            var status = mock(Status.class);
            given(git.status()).willReturn(statusCommand);
            given(statusCommand.call()).willReturn(status);
            given(status.isClean()).willReturn(true);

            given(gitProperties.isPollEnabled()).willReturn(false);
            given(gitPull.getTrigger()).willReturn(null);
            given(gitPull.getLastPullTime()).willReturn(null);
            given(gitPull.getLastPullDuration()).willReturn(null);
            given(gitPull.getLastPullOutcome()).willReturn(null);

            // when
            var result = gitSummaryProvider.generateSummary();

            // then
            assertThat(result.pull().trigger()).isNull();
            assertThat(result.pull().lastPullTime()).isNull();
            assertThat(result.pull().lastPullDuration()).isNull();
            assertThat(result.pull().lastPullOutcome()).isNull();
        }
    }
}
