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
    @Option(names = {"--consul.host"}, defaultValue = "localhost")
    String consulHost;
    @Option(names = {"--consul.port"}, defaultValue = "8500")
    int consulPort;
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

    public static void main(String[] args) {
        try {
            final var exitCode = PicocliRunner.execute(ConsulPopulateCommand.class, args);

            System.exit(exitCode);
        } catch (Exception e) {
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
            log.warn("Nothing done due to --dry-run=true");
        } else {
            populateService.populate();
        }
    }
}
