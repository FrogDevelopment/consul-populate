package com.frogdevelopment.consul.populate;

import static com.frogdevelopment.consul.populate.VertxUtils.toBlocking;
import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static picocli.CommandLine.ExitCode.OK;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;

@Testcontainers
class ConsulPopulateCommandTest {

    @Container
    public static final ConsulContainer CONSUL = new ConsulContainer("hashicorp/consul:1.18.1");

    private final Vertx vertx = Vertx.vertx();

    private String consulHost;
    private String consulPort;
    private ConsulClient consulClient;

    @BeforeEach()
    void beforeEach() {
        var consulClientOptions = new ConsulClientOptions()
                .setHost(CONSUL.getHost())
                .setPort(CONSUL.getMappedPort(8500));
        consulHost = consulClientOptions.getHost();
        consulPort = String.valueOf(consulClientOptions.getPort());

        consulClient = ConsulClient.create(vertx, consulClientOptions);
    }

    @Test
    void should_use_arguments() throws Exception {
        // given
        var properties = new HashMap<String, String>();
        properties.put("consul.host", consulHost);
        properties.put("consul.port", consulPort);
        properties.put("consul.config-version", "1.2.3");
        properties.put("consul.type", "FILES");
        properties.put("consul.files.format", "YAML");
        properties.put("consul.files.target", "prod");
        properties.put("consul.files.root-path", getRootPath());

        String[] args = properties.entrySet()
                .stream()
                .map(entry -> {
                    String key = entry.getKey();
                    var value = entry.getValue();
                    return "--" + key + "=" + value;
                })
                .toArray(String[]::new);

        // when
        int statusCode = catchSystemExit(() -> ConsulPopulateCommand.main(args));

        // then
        assertThat(statusCode).isEqualTo(OK);

        assertConsulKVCorrectlyPopulated();
    }

    @Test
    @Disabled
    void should_use_environmentVariables() throws Exception {
        // given
        withEnvironmentVariable("CONSUL_HOST", consulHost)
                .and("CONSUL_PORT", consulPort)
                .and("CONSUL_CONFIG_VERSION", "1.2.3")
                .and("CONSUL_TYPE", "FILES")
                .and("CONSUL_FILES_FORMAT", "YAML")
                .and("CONSUL_FILES_TARGET", "prod")
                .and("CONSUL_FILES_ROOT_PATH", getRootPath())
                .execute(() -> {
                    // when
                    final String[] args = {};
                    int statusCode = catchSystemExit(() -> ConsulPopulateCommand.main(args));

                    // then
                    assertThat(statusCode).isEqualTo(OK);
                    assertConsulKVCorrectlyPopulated();
                });
    }

    private @NotNull String getRootPath() {
        final var loader = this.getClass().getClassLoader();
        return Optional.ofNullable(loader.getResource("files/application.yaml"))
                .map(url -> Path.of(url.getPath()).getParent().toString())
                .orElseThrow();
    }

    private void assertConsulKVCorrectlyPopulated() {
        var keys = toBlocking(consulClient.getKeys("config"));
        assertThat(keys).hasSize(3);
        assertThat(keys.get(0)).isEqualTo("config/1.2.3/application");
        assertThat(keys.get(1)).isEqualTo("config/1.2.3/application,database");
        assertThat(keys.get(2)).isEqualTo("config/1.2.3/orders-service");

        var kvApplication = toBlocking(consulClient.getValue(keys.get(0)));
        assertThat(kvApplication.isPresent()).isTrue();
        assertThat(kvApplication.getValue()).isEqualToIgnoringNewLines("application: prod");

        var kvDatabase = toBlocking(consulClient.getValue(keys.get(1)));
        assertThat(kvDatabase.isPresent()).isTrue();
        assertThat(kvDatabase.getValue()).isEqualToIgnoringNewLines("database: prod");

        var kvService = toBlocking(consulClient.getValue(keys.get(2)));
        assertThat(kvService.isPresent()).isTrue();
        assertThat(kvService.getValue()).isEqualToIgnoringNewLines("service: prod");
    }
}
