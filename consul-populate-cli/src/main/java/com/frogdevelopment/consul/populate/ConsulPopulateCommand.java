package com.frogdevelopment.consul.populate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.config.GlobalProperties;
import com.frogdevelopment.consul.populate.config.ImportProperties;

import io.micronaut.configuration.picocli.PicocliRunner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Slf4j
@Singleton
@Command(name = "consul-populate",
        description = "Imports configuration files into the KV store of Consul, in transactions of at most 64 "
                + "operations.",
        showDefaultValues = true,
        sortOptions = false,
        footer = {"",
                "Each option can also be given as an environment variable: --consul.kv.prefix is CONSUL_KV_PREFIX,",
                "--consul.files.root-path is CONSUL_FILES_ROOT_PATH. An option takes precedence over the variable.",
                "Exit codes: 0 success, 1 the import failed, 2 invalid command line."})
@RequiredArgsConstructor
public class ConsulPopulateCommand implements Runnable {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help and exit.")
    boolean helpRequested;
    @Option(names = {"--dry-run"}, defaultValue = "false",
            description = "Validate without writing: Consul must be reachable, the files are read and parsed, and the "
                    + "keys to set and to delete are logged. Fails like a real run would.")
    boolean dryRun;
    // Picocli rejects options it does not know, so every property that can be passed as `--consul.xxx` must be
    // declared. ConsulPopulateCommandOptionsTest fails when a property of the configuration classes is missing here.
    @Option(names = {"--consul.uri"}, description = "URI of Consul. Takes precedence over the host and the port.")
    String consulUri;
    @Option(names = {"--consul.host"}, defaultValue = "localhost", description = "Host of Consul.")
    String consulHost;
    @Option(names = {"--consul.port"}, defaultValue = "8500", description = "Port of Consul.")
    int consulPort;
    @Option(names = {"--consul.secured"}, description = "Use HTTPS to reach Consul (default: false).")
    Boolean consulSecured;
    @Option(names = {"--consul.acl-token", "--consul.aclToken"},
            description = "ACL token to read and write the KV. Prefer the CONSUL_ACL_TOKEN variable: an argument is "
                    + "visible in the process list.")
    String aclToken;
    @Option(names = {"--consul.dc"}, description = "Name of the Consul datacenter.")
    String dc;
    @Option(names = {"--consul.timeout"},
            description = "Timeout of the requests to Consul, in milliseconds (default: 30000, 0 for none).")
    Long timeout;
    @Option(names = {"--consul.kv.prefix"}, description = "Prefix of the KV path (default: config).")
    String consulKvPrefix;
    @Option(names = {"--consul.kv.version"}, description = "Version of the configuration, added to the KV path.")
    String consulKvVersion;
    @Option(names = {"--consul.kv.prune"},
            description = "Delete the keys under the KV path that are not in the imported data (default: true).")
    Boolean consulKvPrune;
    @Option(names = {"--consul.kv.max-txn-bytes"},
            description = "Maximum size of the body of a Consul transaction, in bytes (default: 524288, the limit of "
                    + "Consul).")
    Long consulKvMaxTxnBytes;
    @Option(names = {"--consul.files.format"}, defaultValue = "YAML",
            description = "Format of the files: YAML, JSON or PROPERTIES.")
    String consulFileFormat;
    @Option(names = {"--consul.files.target"},
            description = "Subdirectory of the root path whose files override the ones of the root.")
    String consulFileTarget;
    @Option(names = {"--consul.files.rootPath", "--consul.files.root-path"},
            description = "Directory of the configuration files.")
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

    // Providers: the beans are only created when the command runs. `--help` must work without any configuration, and
    // the beans need some (the directory of the files, for instance).
    private final Provider<PopulateService> populateService;
    private final Provider<GlobalProperties> globalProperties;
    private final Provider<ImportProperties> importProperties;

    @Override
    public void run() {
        log.info("Resulting properties from the passed arguments:\n\t- {}\n\t- {}",
                globalProperties.get(), importProperties.get());

        if (dryRun) {
            log.warn("--dry-run=true: the configuration is read and validated, but nothing is written to Consul");
        }
        populateService.get().populate(dryRun);
    }
}
