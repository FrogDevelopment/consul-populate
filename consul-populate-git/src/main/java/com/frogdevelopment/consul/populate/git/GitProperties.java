package com.frogdevelopment.consul.populate.git;

import lombok.Data;

import java.nio.file.Path;
import java.time.Duration;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.frogdevelopment.consul.populate.files.ImportFileProperties;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration properties for git-based import.
 * Maps to properties with prefix {@code consul.git}.
 *
 * <p>Configures repository connection (uri, branch, credentials), local storage path,
 * optional scheduledPoll for changes, and file import settings via nested {@link ImportFileProperties}.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
@Data
@ConfigurationProperties("consul.git")
public class GitProperties {

    /**
     * Git repository URI (e.g., https://github.com/user/repo.git)
     */
    @NotBlank
    private String uri;

    /**
     * Personal access token for authentication (preferred over username/password)
     */
    private String token;

    /**
     * Username for basic authentication
     */
    private String username;

    /**
     * Password for basic authentication
     */
    private String password;

    /**
     * Branch to clone and track (default: "main")
     */
    @NotBlank
    private String branch = "main";

    /**
     * Local directory path where the repository will be cloned (default: /tmp)
     */
    private Path localPath = Path.of("/tmp");

    /**
     * Webhook configuration for receiving push events from git providers (optional)
     */
    @Nullable
    private Webhook webhook;

    /**
     * Whether to delete the cloned repository on server shutdown (default: false)
     */
    private boolean cleanUp = false;

    /**
     * File import settings (format, target, rootPath)
     */
    @ConfigurationBuilder(configurationPrefix = "files")
    private ImportFileProperties fileProperties = new ImportFileProperties();

    /**
     * Whether to enable scheduling pull for repository changes (default: false)
     */
    private boolean pollEnabled = false;

    /**
     * Interval between scheduled pull attempts (default: 5 minutes)
     */
    @NotNull
    private Duration pollInterval = Duration.ofMinutes(5);

    /**
     * Webhook configuration for git provider push events.
     * Maps to properties with prefix {@code consul.git.webhook}.
     *
     * @since 1.2.0
     */
    @Data
    @ConfigurationProperties("webhook")
    public static class Webhook {

        /**
         * The git provider type. Determines which {@code WebhookPayloadHandler} is loaded.
         * <ul>
         *   <li>GITHUB - Uses built-in GitHub handler</li>
         *   <li>BITBUCKET - Uses built-in Bitbucket handler</li>
         *   <li>CUSTOM - User must provide their own WebhookPayloadHandler bean</li>
         * </ul>
         */
        @NotNull
        private GitType type = GitType.GITHUB;

        /**
         * Secret key for webhook signature verification (optional).
         * If set, incoming webhooks must include a valid signature.
         */
        @Nullable
        private String secret;

        /**
         * Supported git provider types for webhooks.
         */
        public enum GitType {
            GITHUB,
            BITBUCKET,
            CUSTOM
        }
    }
}
