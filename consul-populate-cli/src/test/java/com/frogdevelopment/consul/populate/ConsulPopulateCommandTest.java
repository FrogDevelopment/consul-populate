package com.frogdevelopment.consul.populate;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

class ConsulPopulateCommandTest {

    @Test
    @Disabled
    void testWithCommandLineOption() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = new String[]{"-v"};
            PicocliRunner.run(ConsulPopulateCommand.class, ctx, args);

            // consul-populate
            assertTrue(baos.toString().contains("Hi!"));
        }
    }
}
