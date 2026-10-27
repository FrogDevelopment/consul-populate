package com.frogdevelopment.consul.populate.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(MockitoExtension.class)
class GitFactoryTest {

    private static final String URI = "https://github.com/org/project.git";

    @Mock
    private GitProperties gitProperties;
    @Mock
    private RepositoryDirectoryProvider repositoryDirectoryProvider;
    @Mock
    private CloneCommand cloneCommand;
    @Mock
    private Git git;
    @Mock
    private Transport transport;

    private final GitFactory gitFactory = new GitFactory();
    private final Path repositoryDir = Path.of("/tmp/consul-populate-abc/my-repo");

    private MockedStatic<Git> gitStaticMock;
    private MockedStatic<CredentialsProvider> credentialsProviderMock;

    @BeforeEach
    void setUp() {
        gitStaticMock = mockStatic(Git.class);
        credentialsProviderMock = mockStatic(CredentialsProvider.class);
    }

    @AfterEach
    void tearDown() {
        gitStaticMock.close();
        credentialsProviderMock.close();
    }

    @Test
    void shouldCloneWithTheConfiguredRepositoryBranchAndDirectories() throws GitAPIException {
        // given
        givenACloneThatWorks("feature/test");

        // when
        var result = gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        assertThat(result).isSameAs(git);
        var uri = ArgumentCaptor.forClass(String.class);
        var directory = ArgumentCaptor.forClass(File.class);
        var gitDir = ArgumentCaptor.forClass(File.class);
        var branch = ArgumentCaptor.forClass(String.class);
        var remote = ArgumentCaptor.forClass(String.class);
        then(cloneCommand).should().setURI(uri.capture());
        then(cloneCommand).should().setDirectory(directory.capture());
        then(cloneCommand).should().setGitDir(gitDir.capture());
        then(cloneCommand).should().setBranch(branch.capture());
        then(cloneCommand).should().setRemote(remote.capture());
        assertThat(uri.getValue()).isEqualTo(URI);
        assertThat(directory.getValue()).isEqualTo(repositoryDir.toFile());
        assertThat(gitDir.getValue()).isEqualTo(repositoryDir.resolve(".git").toFile());
        assertThat(branch.getValue()).isEqualTo("feature/test");
        assertThat(remote.getValue()).isEqualTo("origin");
    }

    @Test
    void shouldThrowException_whenCloneFails() throws GitAPIException {
        // given
        givenACloneThatWorks("main");
        given(cloneCommand.call()).willThrow(new GitAPIException("Clone failed") {
        });

        // when / then
        assertThatThrownBy(() -> gitFactory.git(gitProperties, repositoryDirectoryProvider))
                .isInstanceOf(GitAPIException.class)
                .hasMessage("Clone failed");
    }

    @Test
    void shouldGiveTheTokenToTheTransport_asTheUserNameWithNoPassword() throws Exception {
        // given
        givenACloneThatWorks("main");
        given(gitProperties.getToken()).willReturn("my-token");

        // when
        gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        assertThat(credentialsGivenToTheTransport()).containsExactly("my-token", "");
    }

    @Test
    void shouldGiveTheUserNameAndPasswordToTheTransport() throws Exception {
        // given
        givenACloneThatWorks("main");
        given(gitProperties.getUsername()).willReturn("username");
        given(gitProperties.getPassword()).willReturn("password");

        // when
        gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        assertThat(credentialsGivenToTheTransport()).containsExactly("username", "password");
    }

    @Test
    void shouldPreferTheToken_whenBothTheTokenAndAUserNameAreConfigured() throws Exception {
        // given
        givenACloneThatWorks("main");
        given(gitProperties.getToken()).willReturn("my-token");

        // when
        gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        assertThat(credentialsGivenToTheTransport()).containsExactly("my-token", "");
        then(gitProperties).should(never()).getUsername();
    }

    @Test
    void shouldFallBackToTheUserName_whenTheTokenIsEmpty() throws Exception {
        // given
        givenACloneThatWorks("main");
        given(gitProperties.getToken()).willReturn("");
        given(gitProperties.getUsername()).willReturn("user");
        given(gitProperties.getPassword()).willReturn("pass");

        // when
        gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        assertThat(credentialsGivenToTheTransport()).containsExactly("user", "pass");
    }

    @Test
    void shouldNotFail_whenTheUserNameHasNoPassword() throws Exception {
        // given - JGit's constructor rejects a null password
        givenACloneThatWorks("main");
        given(gitProperties.getUsername()).willReturn("username");

        // when
        gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        assertThat(credentialsGivenToTheTransport()).containsExactly("username", "");
    }

    @Test
    void shouldCloneAnonymously_whenNoCredentialsAreConfigured() throws Exception {
        // given - a public repository
        givenACloneThatWorks("main");

        // when
        gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        assertThat(credentialsGivenToTheTransport()).isNull();
    }

    @Test
    void shouldCloneAnonymously_whenTheCredentialsAreEmpty() throws Exception {
        // given
        givenACloneThatWorks("main");
        given(gitProperties.getToken()).willReturn("");
        given(gitProperties.getUsername()).willReturn("");

        // when
        gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        assertThat(credentialsGivenToTheTransport()).isNull();
    }

    @Test
    void shouldNeverChangeTheCredentialsOfTheWholeJvm() throws GitAPIException {
        // given - the credentials are given to the transports of the clone and of the fetch, not to every JGit use
        givenACloneThatWorks("main");
        given(gitProperties.getToken()).willReturn("my-token");

        // when
        gitFactory.git(gitProperties, repositoryDirectoryProvider);

        // then
        credentialsProviderMock.verify(() -> CredentialsProvider.setDefault(any()), never());
    }

    @Test
    void shouldNotLogTheCredentialsOfTheUri() throws GitAPIException {
        // given - a URI with the user and the token in it
        givenACloneThatWorks("main");
        given(gitProperties.getUri()).willReturn("https://user:ghp_secrettoken@github.com/org/project.git");
        final var logger = (Logger) LoggerFactory.getLogger(GitFactory.class);
        final var previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        final var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            // when
            gitFactory.git(gitProperties, repositoryDirectoryProvider);

            // then - it is cloned from the URI as configured, and logged without the credentials
            then(cloneCommand).should().setURI("https://user:ghp_secrettoken@github.com/org/project.git");
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("github.com/org/project.git"))
                    .noneMatch(message -> message.contains("ghp_secrettoken") || message.contains("user:"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    private void givenACloneThatWorks(final String branch) throws GitAPIException {
        given(gitProperties.getUri()).willReturn(URI);
        given(gitProperties.getBranch()).willReturn(branch);
        given(gitProperties.isSslVerify()).willReturn(true);
        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDir);

        gitStaticMock.when(Git::cloneRepository).thenReturn(cloneCommand);
        given(cloneCommand.setURI(any())).willReturn(cloneCommand);
        given(cloneCommand.setDirectory(any())).willReturn(cloneCommand);
        given(cloneCommand.setGitDir(any())).willReturn(cloneCommand);
        given(cloneCommand.setBranch(any())).willReturn(cloneCommand);
        given(cloneCommand.setRemote(any())).willReturn(cloneCommand);
        given(cloneCommand.setTransportConfigCallback(any())).willReturn(cloneCommand);
        given(cloneCommand.call()).willReturn(git);
    }

    /**
     * Runs the callback given to the clone on a transport, as JGit does.
     *
     * @return the user name and the password that the transport was given, or {@code null} if it got no credentials
     */
    private List<String> credentialsGivenToTheTransport() throws URISyntaxException {
        var callback = ArgumentCaptor.forClass(TransportConfigCallback.class);
        then(cloneCommand).should().setTransportConfigCallback(callback.capture());
        callback.getValue().configure(transport);

        var provider = ArgumentCaptor.forClass(CredentialsProvider.class);
        then(transport).should(atLeast(0)).setCredentialsProvider(provider.capture());
        if (provider.getAllValues().isEmpty()) {
            return null;
        }
        var user = new CredentialItem.Username();
        var password = new CredentialItem.Password();
        provider.getValue().get(new URIish(URI), user, password);
        return List.of(user.getValue(), new String(password.getValue()));
    }
}
