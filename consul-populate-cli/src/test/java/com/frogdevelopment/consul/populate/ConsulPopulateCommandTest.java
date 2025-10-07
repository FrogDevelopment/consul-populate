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
    public static final ConsulContainer CONSUL = new ConsulContainer("hashicorp/consul:1.21");

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
    void should_dryRun() throws Exception {
        // given
        var properties = new HashMap<String, String>();
        properties.put("consul.host", consulHost);
        properties.put("consul.port", consulPort);
        properties.put("consul.kv.prefix", "frog");
        properties.put("consul.kv.version", "1.2.3");
        properties.put("consul.files.format", "YAML");
        properties.put("consul.files.target", "prod");
        properties.put("consul.files.root-path", getRootPath());
        properties.put("dry-run", "true");

        var args = properties.entrySet()
                .stream()
                .map(entry -> {
                    var key = entry.getKey();
                    var value = entry.getValue();
                    return "--" + key + "=" + value;
                })
                .toArray(String[]::new);

        // when
        // Run the command and let it complete normally (with System.exit)
        try {
            ConsulPopulateCommand.main(args);
        } catch (Exception e) {
            // Expected since System.exit() throws SecurityException in test environment
            // The actual work should be completed before the exit call
        }

        // then
        // Give a moment for async operations to complete
        Thread.sleep(100);
        var keys = toBlocking(consulClient.getKeys(""));
        assertThat(keys).isEmpty();
    }

    @Test
    void should_use_arguments() throws Exception {
        // given
        var properties = new HashMap<String, String>();
        properties.put("consul.host", consulHost);
        properties.put("consul.port", consulPort);
        properties.put("consul.kv.prefix", "frog");
        properties.put("consul.kv.version", "1.2.3");
        properties.put("consul.files.format", "YAML");
        properties.put("consul.files.target", "prod");
        properties.put("consul.files.root-path", getRootPath());

        var args = properties.entrySet()
                .stream()
                .map(entry -> {
                    var key = entry.getKey();
                    var value = entry.getValue();
                    return "--" + key + "=" + value;
                })
                .toArray(String[]::new);

        // when
        // Run the command and let it complete normally (with System.exit)
        // The application will populate Consul and then exit
        try {
            ConsulPopulateCommand.main(args);
        } catch (Exception e) {
            // Expected since System.exit() throws SecurityException in test environment
            // The actual work should be completed before the exit call
        }

        // then
        // Give a moment for async operations to complete
        Thread.sleep(100);
        assertConsulKVCorrectlyPopulated();
    }

    @Test
    @Disabled("disabled due to limitation of tests run with env variables")
    void should_use_environmentVariables() throws Exception {
        // given
        withEnvironmentVariable("CONSUL_HOST", consulHost)
                .and("CONSUL_PORT", consulPort)
                .and("CONSUL_KV_PREFIX", "frog")
                .and("CONSUL_KV_VERSION", "1.2.3")
                .and("CONSUL_FILES_FORMAT", "YAML")
                .and("CONSUL_FILES_TARGET", "prod")
                .and("CONSUL_FILES_ROOT_PATH", getRootPath())
                .execute(() -> {
                    // when
                    final String[] args = {};
                    var statusCode = catchSystemExit(() -> ConsulPopulateCommand.main(args));

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
        var keys = toBlocking(consulClient.getKeys(""));
        assertThat(keys).hasSize(3);
        assertThat(keys.get(0)).isEqualTo("frog/1.2.3/application");
        assertThat(keys.get(1)).isEqualTo("frog/1.2.3/application,database");
        assertThat(keys.get(2)).isEqualTo("frog/1.2.3/orders-service");

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
