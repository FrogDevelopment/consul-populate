package com.frogdevelopment.consul.populate.files;

import static com.frogdevelopment.consul.populate.VertxUtils.toBlocking;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.frogdevelopment.consul.populate.DataImporter;
import com.frogdevelopment.consul.populate.PopulateService;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.NonNull;
import io.vertx.ext.consul.ConsulClient;

@Testcontainers
@Property(name = "consul.kv.version", value = "test")
@Property(name = "consul.files.format", value = "YAML")
class FilesPopulateServiceImplTest extends BaseFilesImporterTest {

    @Container
    public static final ConsulContainer CONSUL = new ConsulContainer("hashicorp/consul:1.18.1");

    @Inject
    private PopulateService populateService;

    @Inject
    private ConsulClient consulClient;

    @Inject
    private DataImporter dataImporter;

    @Override
    public @NonNull Map<String, String> getProperties() {
        CONSUL.start();
        var properties = new HashMap<>(super.getProperties());
        properties.put("consul.host", CONSUL.getHost());
        properties.put("consul.port", String.valueOf(CONSUL.getMappedPort(8500)));

        return properties;
    }

    @Test
    void should_populate_consul() {
        // given
        var keys = toBlocking(consulClient.getKeys("config"));
        assertThat(keys).isEmpty();

        // when
        populateService.populate();

        // then
        assertThat(dataImporter).isInstanceOf(FilesImporter.class);
        keys = toBlocking(consulClient.getKeys("config"));
        assertThat(keys).hasSize(1);
        final var key = keys.getFirst();
        assertThat(key).isEqualTo("config/test/application");

        var kv = toBlocking(consulClient.getValue("config/test/application"));
        assertThat(kv.isPresent()).isTrue();
        assertThat(kv.getValue()).isEqualTo(YamlFilesImporterTest.EXPECTED);
    }

}
