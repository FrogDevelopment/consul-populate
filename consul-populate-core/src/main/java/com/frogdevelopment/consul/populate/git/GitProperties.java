package com.frogdevelopment.consul.populate.git;

import lombok.Data;

import java.nio.file.Path;
import java.time.Duration;

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
 * optional polling for changes, and file import settings via nested {@link ImportFileProperties}.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
@Data
@ConfigurationProperties("consul.git")
public class GitProperties {

    /** Git repository URI (e.g., https://github.com/user/repo.git) */
    @NotBlank
    private String uri;

    /** Personal access token for authentication (preferred over username/password) */
//    @NotBlank
    private String token;

    /** Username for basic authentication */
    private String username;

    /** Password for basic authentication */
    private String password;

    /** Branch to clone and track (default: "main") */
    @NotBlank
    private String branch = "main";

    /** Local directory path where the repository will be cloned (default: /tmp) */
    private Path localPath = Path.of("/tmp");

    /** Polling configuration for detecting repository changes */
    private Polling polling = new Polling();

    /** File import settings (format, target, rootPath) */
    @ConfigurationBuilder(configurationPrefix = "files")
    private ImportFileProperties fileProperties = new ImportFileProperties();

    /**
     * Polling configuration for detecting repository changes.
     * Maps to properties with prefix {@code consul.git.polling}.
     */
    @Data
    @ConfigurationProperties("polling")
    public static class Polling {

        /** Whether to enable polling for repository changes (default: false) */
        private boolean enabled = false;

        /** Delay between polling attempts (default: 5 minutes) */
        @NotNull
        private Duration delay = Duration.ofMinutes(5);

    }
}
