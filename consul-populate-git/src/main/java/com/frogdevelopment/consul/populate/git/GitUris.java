package com.frogdevelopment.consul.populate.git;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

import org.eclipse.jgit.transport.URIish;

/**
 * Helpers on the URI of the git repository.
 *
 * @author Le Gall Benoît
 * @since 1.4.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GitUris {

    /** The {@code user:password@} of {@code https://user:password@host/path}. */
    private static final Pattern USER_INFO = Pattern.compile("(?<=//)[^/@\\s]*@");

    /**
     * Removes the credentials a URI may carry ({@code https://user:token@host/...}), for what is logged or displayed.
     *
     * @param uri the URI of the repository
     * @return the URI without user and password; when it cannot be parsed, the user information is still removed
     */
    public static String mask(final String uri) {
        if (uri == null) {
            return null;
        }
        try {
            return new URIish(uri).setUser(null).setPass(null).toString();
        } catch (final Exception e) {
            return USER_INFO.matcher(uri).replaceFirst("");
        }
    }
}
