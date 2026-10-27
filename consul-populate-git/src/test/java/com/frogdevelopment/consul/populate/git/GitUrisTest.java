package com.frogdevelopment.consul.populate.git;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GitUrisTest {

    @ParameterizedTest
    @CsvSource({
            "https://github.com/org/repo.git,                       https://github.com/org/repo.git",
            "https://user:ghp_token@github.com/org/repo.git,        https://github.com/org/repo.git",
            "https://ghp_token@github.com/org/repo.git,             https://github.com/org/repo.git",
            "https://user:p%40ss@git.example.com:8443/org/repo.git, https://git.example.com:8443/org/repo.git",
    })
    void mask_shouldRemoveTheCredentialsOfTheUri(final String uri, final String expected) {
        assertThat(GitUris.mask(uri)).isEqualTo(expected);
    }

    @Test
    void mask_shouldStillRemoveTheCredentials_whenTheUriCannotBeParsed() {
        // given - a space makes it invalid, which must not lead to logging the credentials
        var invalid = "https://user:secret@git host/org/repo.git";

        // when
        var masked = GitUris.mask(invalid);

        // then
        assertThat(masked).doesNotContain("secret").doesNotContain("user:");
    }

    @Test
    void mask_shouldKeepAnInvalidUriWithoutCredentialsAsItIs() {
        assertThat(GitUris.mask("not a valid uri with spaces")).isEqualTo("not a valid uri with spaces");
    }

    @Test
    void mask_shouldAcceptNull() {
        assertThat(GitUris.mask(null)).isNull();
    }
}
