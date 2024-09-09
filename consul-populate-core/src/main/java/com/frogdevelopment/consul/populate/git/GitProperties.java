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
 * Properties for Git import
 *
 * @author Le Gall Benoît
 * @since 1.1.0
 */
@Data
@ConfigurationProperties("consul.git")
public class GitProperties {

    @NotBlank
    private String uri;

//    @NotBlank
    private String token;
    private String username;
    private String password;

    @NotBlank
    private String branch = "main";

    private Path localPath = Path.of("/tmp");

    private Polling polling = new Polling();

    @ConfigurationBuilder(configurationPrefix = "files")
    private ImportFileProperties fileProperties = new ImportFileProperties();

    @Data
    @ConfigurationProperties("polling")
    public static class Polling {

        private boolean enabled = false;

        @NotNull
        private Duration delay = Duration.ofMinutes(5);

    }
}
