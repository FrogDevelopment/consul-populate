package com.frogdevelopment.consul.populate;

import static com.frogdevelopment.consul.populate.VertxUtils.toBlocking;
import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static org.assertj.core.api.Assertions.assertThat;
import static picocli.CommandLine.ExitCode.OK;
import static picocli.CommandLine.ExitCode.SOFTWARE;
import static picocli.CommandLine.ExitCode.USAGE;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
    public static final ConsulContainer CONSUL = new ConsulContainer("hashicorp/consul:1.22.7");

    /** ACLs enabled with a default "deny" policy, and no token given to the CLI: every KV access is refused. */
    @Container
    public static final ConsulContainer CONSUL_WITH_ACL = new ConsulContainer("hashicorp/consul:1.22.7")
            .withEnv("CONSUL_LOCAL_CONFIG", "{\"acl\": {\"enabled\": true, \"default_policy\": \"deny\"}}");

    private final Vertx vertx = Vertx.vertx();

    private String consulHost;
    private String consulPort;
    private ConsulClient consulClient;

    @BeforeEach
    void beforeEach() {
        var consulClientOptions = new ConsulClientOptions()
                .setHost(CONSUL.getHost())
                .setPort(CONSUL.getMappedPort(8500));
        consulHost = consulClientOptions.getHost();
        consulPort = String.valueOf(consulClientOptions.getPort());

        consulClient = ConsulClient.create(vertx, consulClientOptions);
        // tests share the container: start each of them from an empty KV
        toBlocking(consulClient.deleteValues(""));
    }

    @AfterEach
    void afterEach() {
        consulClient.close();
        vertx.close();
    }

    @Test
    void should_populate_consul_and_exit_with_ok() throws Exception {
        // when
        var exitCode = run(arguments());

        // then
        assertThat(exitCode).isEqualTo(OK);
        assertConsulKVCorrectlyPopulated();
    }

    @Test
    void should_accept_the_uri_and_secured_arguments() throws Exception {
        // given - neither option was accepted before: picocli answered "Unknown option"
        var args = new ArrayList<>(arguments());
        args.removeIf(arg -> arg.startsWith("--consul.host=") || arg.startsWith("--consul.port="));
        args.add("--consul.uri=http://" + consulHost + ":" + consulPort);
        args.add("--consul.secured=false");

        // when
        var exitCode = run(args);

        // then
        assertThat(exitCode).isEqualTo(OK);
        assertConsulKVCorrectlyPopulated();
    }

    @Test
    void should_not_write_anything_on_dry_run() throws Exception {
        // given
        var args = new ArrayList<>(arguments());
        args.add("--dry-run=true");

        // when
        var exitCode = run(args);

        // then
        assertThat(exitCode).isEqualTo(OK);
        assertThat(toBlocking(consulClient.getKeys(""))).isEmpty();
    }

    @Test
    void should_fail_on_dry_run_when_the_files_directory_does_not_exist() throws Exception {
        // given - a pull request validation step must not be green for a wrong path
        var args = new ArrayList<>(arguments());
        args.removeIf(arg -> arg.startsWith("--consul.files.root-path="));
        args.add("--consul.files.root-path=/nonexistent");
        args.add("--dry-run=true");

        // when
        var exitCode = run(args);

        // then
        assertThat(exitCode).isEqualTo(SOFTWARE);
    }

    @Test
    void should_fail_on_dry_run_when_consul_is_not_reachable() throws Exception {
        // given
        var args = new ArrayList<>(arguments());
        args.removeIf(arg -> arg.startsWith("--consul.port="));
        args.add("--consul.port=1");
        args.add("--consul.timeout=2000");
        args.add("--dry-run=true");

        // when
        var exitCode = run(args);

        // then
        assertThat(exitCode).isEqualTo(SOFTWARE);
    }

    @Test
    void should_fail_when_the_files_directory_does_not_exist() throws Exception {
        // given
        var args = new ArrayList<>(arguments());
        args.removeIf(arg -> arg.startsWith("--consul.files.root-path="));
        args.add("--consul.files.root-path=/nonexistent");

        // when
        var exitCode = run(args);

        // then
        assertThat(exitCode).isEqualTo(SOFTWARE);
        assertThat(toBlocking(consulClient.getKeys(""))).isEmpty();
    }

    @Test
    void should_fail_when_consul_is_not_reachable() throws Exception {
        // given
        var args = new ArrayList<>(arguments());
        args.removeIf(arg -> arg.startsWith("--consul.port="));
        args.add("--consul.port=1");
        args.add("--consul.timeout=2000");

        // when
        var exitCode = run(args);

        // then
        assertThat(exitCode).isEqualTo(SOFTWARE);
    }

    @Test
    void should_fail_when_consul_denies_access() throws Exception {
        // given
        var args = new ArrayList<>(arguments());
        args.removeIf(arg -> arg.startsWith("--consul.host=") || arg.startsWith("--consul.port="));
        args.add("--consul.host=" + CONSUL_WITH_ACL.getHost());
        args.add("--consul.port=" + CONSUL_WITH_ACL.getMappedPort(8500));

        // when
        var exitCode = run(args);

        // then
        assertThat(exitCode).isEqualTo(SOFTWARE);
    }

    @Test
    void should_fail_with_usage_error_when_an_option_is_unknown() throws Exception {
        // given
        var args = new ArrayList<>(arguments());
        args.add("--consul.unknown=value");

        // when
        var exitCode = run(args);

        // then
        assertThat(exitCode).isEqualTo(USAGE);
        assertThat(toBlocking(consulClient.getKeys(""))).isEmpty();
    }

    /**
     * Runs the packaged fat JAR in its own JVM: environment variables cannot be changed in the test JVM, and it checks
     * that the artifact that is published is runnable (merged service files, main class).
     */
    @Test
    void should_use_environment_variables() throws Exception {
        // given
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var builder = new ProcessBuilder(java, "-jar", System.getProperty("cli.jar"))
                .redirectErrorStream(true);
        builder.environment().putAll(Map.of(
                "CONSUL_HOST", consulHost,
                "CONSUL_PORT", consulPort,
                "CONSUL_KV_PREFIX", "frog",
                "CONSUL_KV_VERSION", "1.2.3",
                "CONSUL_FILES_FORMAT", "YAML",
                "CONSUL_FILES_TARGET", "prod",
                "CONSUL_FILES_ROOT_PATH", getRootPath()));

        // when
        var process = builder.start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // then
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("process did not terminate:%n%s", output).isTrue();
        assertThat(process.exitValue()).as("exit code, output:%n%s", output).isEqualTo(OK);
        assertConsulKVCorrectlyPopulated();
    }

    private List<String> arguments() {
        return List.of(
                "--consul.host=" + consulHost,
                "--consul.port=" + consulPort,
                "--consul.kv.prefix=frog",
                "--consul.kv.version=1.2.3",
                "--consul.files.format=YAML",
                "--consul.files.target=prod",
                "--consul.files.root-path=" + getRootPath());
    }

    /**
     * @return the exit code of {@link ConsulPopulateCommand#main(String[])}, which always ends with
     * {@link System#exit(int)}
     */
    private static int run(final List<String> args) throws Exception {
        return catchSystemExit(() -> ConsulPopulateCommand.main(args.toArray(String[]::new)));
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
