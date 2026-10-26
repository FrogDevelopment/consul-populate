package com.frogdevelopment.consul.populate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.config.GlobalProperties;
import com.frogdevelopment.consul.populate.config.ImportProperties;

import io.micronaut.configuration.picocli.PicocliRunner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Slf4j
@Singleton
@Command(name = "consul-populate")
@RequiredArgsConstructor
public class ConsulPopulateCommand implements Runnable {

    @Option(names = {"--dry-run"}, defaultValue = "false")
    boolean dryRun;
    // Picocli rejects options it does not know, so every property that can be passed as `--consul.xxx` must be declared.
    // ConsulPopulateCommandOptionsTest fails when a property of the configuration classes is missing here.
    @Option(names = {"--consul.uri"})
    String consulUri;
    @Option(names = {"--consul.host"}, defaultValue = "localhost")
    String consulHost;
    @Option(names = {"--consul.port"}, defaultValue = "8500")
    int consulPort;
    @Option(names = {"--consul.secured"})
    Boolean consulSecured;
    @Option(names = {"--consul.acl-token", "--consul.aclToken"})
    String aclToken;
    @Option(names = {"--consul.dc"})
    String dc;
    @Option(names = {"--consul.timeout"})
    Long timeout;
    @Option(names = {"--consul.kv.prefix"})
    String consulKvPrefix;
    @Option(names = {"--consul.kv.version"})
    String consulKvVersion;
    @Option(names = {"--consul.files.format"}, defaultValue = "YAML")
    String consulFileFormat;
    @Option(names = {"--consul.files.target"})
    String consulFileTarget;
    @Option(names = {"--consul.files.rootPath", "--consul.files.root-path"})
    String consulFileRootPath;

    public static void main(final String[] args) {
        try {
            final var exitCode = PicocliRunner.execute(ConsulPopulateCommand.class, args);

            System.exit(exitCode);
        } catch (final Exception e) {
            log.error("Unexpected exception", e);
            System.exit(CommandLine.ExitCode.SOFTWARE);
        }
    }

    private final PopulateService populateService;
    private final GlobalProperties globalProperties;
    private final ImportProperties importProperties;

    @Override
    public void run() {
        log.info("Resulting properties from the passed arguments:\n\t- {}\n\t- {}", globalProperties, importProperties);

        if (dryRun) {
            log.warn("--dry-run=true: the configuration is read and validated, but nothing is written to Consul");
        }
        populateService.populate(dryRun);
    }
}
