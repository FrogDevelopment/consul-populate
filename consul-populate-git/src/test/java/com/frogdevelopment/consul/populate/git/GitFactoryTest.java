package com.frogdevelopment.consul.populate.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.nio.file.Path;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GitFactoryTest {

    @Mock
    private GitProperties gitProperties;
    @Mock
    private RepositoryDirectoryProvider repositoryDirectoryProvider;
    @Mock
    private CloneCommand cloneCommand;
    @Mock
    private Git git;

    private GitFactory gitFactory;
    private MockedStatic<Git> gitStaticMock;
    private MockedStatic<CredentialsProvider> credentialsProviderMock;

    @BeforeEach
    void setUp() {
        gitFactory = new GitFactory();
        gitStaticMock = mockStatic(Git.class);
        credentialsProviderMock = mockStatic(CredentialsProvider.class);
    }

    @AfterEach
    void tearDown() {
        gitStaticMock.close();
        credentialsProviderMock.close();
    }

    @Test
    void shouldCloneRepository_withTokenAuthentication() throws GitAPIException {
        // given
        var repositoryDir = Path.of("/tmp/repo");
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
        given(gitProperties.getToken()).willReturn("my-token");
        given(gitProperties.getBranch()).willReturn("main");
        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDir);

        gitStaticMock.when(Git::cloneRepository).thenReturn(cloneCommand);
        given(cloneCommand.setURI(any())).willReturn(cloneCommand);
        given(cloneCommand.setDirectory(any())).willReturn(cloneCommand);
        given(cloneCommand.setGitDir(any())).willReturn(cloneCommand);
        given(cloneCommand.setBranch(any())).willReturn(cloneCommand);
        given(cloneCommand.setRemote(any())).willReturn(cloneCommand);
        given(cloneCommand.call()).willReturn(git);

        // when
        var result = gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        assertThat(result).isSameAs(git);

        var credentialsCaptor = ArgumentCaptor.forClass(CredentialsProvider.class);
        credentialsProviderMock.verify(() -> CredentialsProvider.setDefault(credentialsCaptor.capture()));

        var credentials = (UsernamePasswordCredentialsProvider) credentialsCaptor.getValue();
        assertThat(credentials).isNotNull();
    }

    @Test
    void shouldCloneRepository_withUsernamePasswordAuthentication() throws GitAPIException {
        // given
        var repositoryDir = Path.of("/tmp/repo");
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
        given(gitProperties.getToken()).willReturn(null);
        given(gitProperties.getUsername()).willReturn("username");
        given(gitProperties.getPassword()).willReturn("password");
        given(gitProperties.getBranch()).willReturn("develop");
        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDir);

        gitStaticMock.when(Git::cloneRepository).thenReturn(cloneCommand);
        given(cloneCommand.setURI(any())).willReturn(cloneCommand);
        given(cloneCommand.setDirectory(any())).willReturn(cloneCommand);
        given(cloneCommand.setGitDir(any())).willReturn(cloneCommand);
        given(cloneCommand.setBranch(any())).willReturn(cloneCommand);
        given(cloneCommand.setRemote(any())).willReturn(cloneCommand);
        given(cloneCommand.call()).willReturn(git);

        // when
        var result = gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        assertThat(result).isSameAs(git);
    }

    @Test
    void shouldPreferToken_whenBothTokenAndUsernamePasswordProvided() throws GitAPIException {
        // given
        var repositoryDir = Path.of("/tmp/repo");
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
        given(gitProperties.getToken()).willReturn("my-token");
        given(gitProperties.getBranch()).willReturn("main");
        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDir);

        gitStaticMock.when(Git::cloneRepository).thenReturn(cloneCommand);
        given(cloneCommand.setURI(any())).willReturn(cloneCommand);
        given(cloneCommand.setDirectory(any())).willReturn(cloneCommand);
        given(cloneCommand.setGitDir(any())).willReturn(cloneCommand);
        given(cloneCommand.setBranch(any())).willReturn(cloneCommand);
        given(cloneCommand.setRemote(any())).willReturn(cloneCommand);
        given(cloneCommand.call()).willReturn(git);

        // when
        gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        var credentialsCaptor = ArgumentCaptor.forClass(CredentialsProvider.class);
        credentialsProviderMock.verify(() -> CredentialsProvider.setDefault(credentialsCaptor.capture()));
        then(gitProperties).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldSetCorrectCloneParameters() throws GitAPIException {
        // given
        var repositoryDir = Path.of("/tmp/consul-populate-abc/my-repo");
        var uri = "https://github.com/org/project.git";
        var branch = "feature/test";

        given(gitProperties.getUri()).willReturn(uri);
        given(gitProperties.getToken()).willReturn("token");
        given(gitProperties.getBranch()).willReturn(branch);
        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDir);

        gitStaticMock.when(Git::cloneRepository).thenReturn(cloneCommand);
        given(cloneCommand.setURI(any())).willReturn(cloneCommand);
        given(cloneCommand.setDirectory(any())).willReturn(cloneCommand);
        given(cloneCommand.setGitDir(any())).willReturn(cloneCommand);
        given(cloneCommand.setBranch(any())).willReturn(cloneCommand);
        given(cloneCommand.setRemote(any())).willReturn(cloneCommand);
        given(cloneCommand.call()).willReturn(git);

        // when
        gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        var uriCaptor = ArgumentCaptor.forClass(String.class);
        var directoryCaptor = ArgumentCaptor.forClass(File.class);
        var gitDirCaptor = ArgumentCaptor.forClass(File.class);
        var branchCaptor = ArgumentCaptor.forClass(String.class);
        var remoteCaptor = ArgumentCaptor.forClass(String.class);

        gitStaticMock.verify(Git::cloneRepository);
        credentialsProviderMock.verify(() -> CredentialsProvider.setDefault(any()));
    }

    @Test
    void shouldSetGitDirToRepositoryDotGit() throws GitAPIException {
        // given
        var repositoryDir = Path.of("/tmp/repo");
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
        given(gitProperties.getToken()).willReturn("token");
        given(gitProperties.getBranch()).willReturn("main");
        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDir);

        gitStaticMock.when(Git::cloneRepository).thenReturn(cloneCommand);
        given(cloneCommand.setURI(any())).willReturn(cloneCommand);
        given(cloneCommand.setDirectory(any())).willReturn(cloneCommand);
        given(cloneCommand.setGitDir(any())).willReturn(cloneCommand);
        given(cloneCommand.setBranch(any())).willReturn(cloneCommand);
        given(cloneCommand.setRemote(any())).willReturn(cloneCommand);
        given(cloneCommand.call()).willReturn(git);

        // when
        gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        var gitDirCaptor = ArgumentCaptor.forClass(File.class);
        gitStaticMock.verify(Git::cloneRepository);
    }

    @Test
    void shouldThrowException_whenCloneFails() throws GitAPIException {
        // given
        var repositoryDir = Path.of("/tmp/repo");
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
        given(gitProperties.getToken()).willReturn("token");
        given(gitProperties.getBranch()).willReturn("main");
        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDir);

        gitStaticMock.when(Git::cloneRepository).thenReturn(cloneCommand);
        given(cloneCommand.setURI(any())).willReturn(cloneCommand);
        given(cloneCommand.setDirectory(any())).willReturn(cloneCommand);
        given(cloneCommand.setGitDir(any())).willReturn(cloneCommand);
        given(cloneCommand.setBranch(any())).willReturn(cloneCommand);
        given(cloneCommand.setRemote(any())).willReturn(cloneCommand);
        given(cloneCommand.call()).willThrow(new GitAPIException("Clone failed") {});

        // when/then
        assertThatThrownBy(() -> gitFactory.git(gitProperties, repositoryDirectoryProvider))
                .isInstanceOf(GitAPIException.class)
                .hasMessage("Clone failed");
    }

    @Test
    void shouldSetDefaultRemoteName() throws GitAPIException {
        // given
        var repositoryDir = Path.of("/tmp/repo");
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
        given(gitProperties.getToken()).willReturn("token");
        given(gitProperties.getBranch()).willReturn("main");
        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDir);

        gitStaticMock.when(Git::cloneRepository).thenReturn(cloneCommand);
        given(cloneCommand.setURI(any())).willReturn(cloneCommand);
        given(cloneCommand.setDirectory(any())).willReturn(cloneCommand);
        given(cloneCommand.setGitDir(any())).willReturn(cloneCommand);
        given(cloneCommand.setBranch(any())).willReturn(cloneCommand);
        given(cloneCommand.setRemote(any())).willReturn(cloneCommand);
        given(cloneCommand.call()).willReturn(git);

        // when
        gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        var remoteCaptor = ArgumentCaptor.forClass(String.class);
        gitStaticMock.verify(Git::cloneRepository);
    }

    @Test
    void shouldHandleEmptyToken() throws GitAPIException {
        // given
        var repositoryDir = Path.of("/tmp/repo");
        given(gitProperties.getUri()).willReturn("https://github.com/user/repo.git");
        given(gitProperties.getToken()).willReturn("");
        given(gitProperties.getUsername()).willReturn("user");
        given(gitProperties.getPassword()).willReturn("pass");
        given(gitProperties.getBranch()).willReturn("main");
        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDir);

        gitStaticMock.when(Git::cloneRepository).thenReturn(cloneCommand);
        given(cloneCommand.setURI(any())).willReturn(cloneCommand);
        given(cloneCommand.setDirectory(any())).willReturn(cloneCommand);
        given(cloneCommand.setGitDir(any())).willReturn(cloneCommand);
        given(cloneCommand.setBranch(any())).willReturn(cloneCommand);
        given(cloneCommand.setRemote(any())).willReturn(cloneCommand);
        given(cloneCommand.call()).willReturn(git);

        // when
        var result = gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        assertThat(result).isSameAs(git);
    }
}
