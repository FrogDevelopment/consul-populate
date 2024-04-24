package com.frogdevelopment.consul.populate.config;

import lombok.Data;

import java.util.Optional;
import java.util.OptionalLong;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;

@Data
@Context
@ConfigurationProperties("consul")
public class GlobalProperties {

    /**
     * Consul host. Defaults to {@code localhost}
     */
    @NotBlank
    private String host = "localhost";

    /**
     * Consul port. Defaults to {@code 8500}
     */
    private int port = 8500;

    /**
     * Set whether Consul is secured. Defaults to {@code false}
     */
    private boolean secured = false;

    /**
     * ACL token needed to read and write in KV path. When present, will be added to requests using `?token` query parameter
     */
    private Optional<String> aclToken = Optional.empty();

    /**
     * Consul datacenter name. When present, will be added to requests using `?dc` query parameter
     */
    private Optional<String> dc = Optional.empty();

    /**
     * Amount of time (in milliseconds) for requests
     */
    private OptionalLong timeout = OptionalLong.empty();

    /**
     * Type of data used to be export into Consul
     */
    @NotNull
    private Type type;

    @NotNull
    private KV kv = new KV();

    enum Type {
        FILES, GIT
    }

    @Data
    @ConfigurationProperties("kv")
    public static class KV {

        /**
         * Prefix for the KV path where the configuration is stored. Defaults to {@code config}
         */
        @Pattern(regexp = "[\\w\\-]+")
        private String prefix = "config";

        /**
         * Version of the configuration. When present, will be used in the KV path
         */
        private Optional<@Pattern(regexp = "[\\w\\-]+") String> version = Optional.empty();

        /**
         * @return Path by concatenating {@code kv.path} & {@code kv.version} if present
         */
        public String getPath() {
            return version.map(value -> prefix + "/" + value).orElse(prefix);
        }
    }
}
