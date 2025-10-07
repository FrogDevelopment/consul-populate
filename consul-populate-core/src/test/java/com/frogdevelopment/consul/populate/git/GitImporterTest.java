package com.frogdevelopment.consul.populate.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;

import com.frogdevelopment.consul.populate.DataImporter;
import com.frogdevelopment.consul.populate.PopulateService;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;

@MicronautTest(startApplication = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GitImporterTest implements TestPropertyProvider {

    @Container
    public static final ConsulContainer CONSUL = new ConsulContainer("hashicorp/consul:1.21");

    @Inject
    private PopulateService populateService;

    @Inject
    private DataImporter dataImporter;

    @Override
    public @NonNull Map<String, String> getProperties() {
        CONSUL.start();
        var properties = new HashMap<String, String>();
        properties.put("consul.host", CONSUL.getHost());
        properties.put("consul.port", String.valueOf(CONSUL.getMappedPort(8500)));
        properties.put("consul.git.uri", "something");

        return properties;
    }

    @Test
    void should_throw_UnsupportedOperationException() {
        // given

        // when
        var caught = catchException(() -> populateService.populate());

        // then
        assertThat(caught).isInstanceOf(UnsupportedOperationException.class);
        assertThat(dataImporter).isInstanceOf(GitImporter.class);
    }
}
