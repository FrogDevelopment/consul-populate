package com.frogdevelopment.consul.populate;

import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static org.assertj.core.api.Assertions.assertThat;
import static picocli.CommandLine.ExitCode.OK;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * `--help` has to work on a machine with no configuration at all: that is when somebody reads it. No container here,
 * and nothing in the environment of the test sets the directory of the files.
 */
class ConsulPopulateCommandHelpTest {

    @Test
    void should_print_the_usage_and_exit_with_ok_without_any_configuration() throws Exception {
        assertUsageIsPrinted("--help");
    }

    @Test
    void should_accept_the_short_form() throws Exception {
        assertUsageIsPrinted("-h");
    }

    private static void assertUsageIsPrinted(final String option) throws Exception {
        // when
        final var exitCode = new AtomicInteger(-1);
        final var output = tapSystemOut(() ->
                exitCode.set(catchSystemExit(() -> ConsulPopulateCommand.main(new String[] {option}))));

        // then - the usage, not a stack trace about a missing bean
        assertThat(exitCode).hasValue(OK);
        assertThat(output)
                .contains("Usage: consul-populate")
                .contains("--dry-run")
                .contains("--consul.host")
                .contains("--consul.files.root-path")
                .contains("CONSUL_KV_PREFIX")
                .doesNotContain("DependencyInjectionException")
                .doesNotContain("NoSuchBeanException");
    }
}
