package com.frogdevelopment.consul.populate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import io.micronaut.configuration.picocli.PicocliRunner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Slf4j
@Singleton
@Command(name = "consul-populate")
@RequiredArgsConstructor
public class ConsulPopulateCommand implements Runnable {

    @Option(names = {"-v", "--verbose"}, description = "...")
    boolean verbose;

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

    public void run() {
        // business logic here
        if (verbose) {
            System.out.println("Hi!");
        }

        populateService.populate();
    }
}
