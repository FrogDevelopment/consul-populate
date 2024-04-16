package com.frogdevelopment.consul.populate.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.nio.file.Path;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
@Property(name = "consul.type", value = "FILES")
@Property(name = "consul.files.format", value = "YAML")
class YamlFilesImporterIntegrationTest {

    @Inject
    private YamlFilesImporter filesImporter;
    @Inject
    private ResourceLoader loader;

    @Inject
    private ConsulFileProperties consulFileProperties;

    @MockBean(ConsulFileProperties.class)
    ConsulFileProperties consulFileProperties() {
        return Mockito.mock(ConsulFileProperties.class);
    }

    @Test
    void should_mergeYaml() throws IOException {
        // given
        final var rootUrl = loader.getResource("classpath:files/application.yaml");
        given(consulFileProperties.getRootPath()).willReturn(rootUrl
                .map(url -> Path.of(url.getPath()).getParent().toString())
                .orElseThrow());
        given(consulFileProperties.getTarget()).willReturn("test");

        // when
        final var result = filesImporter.execute();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get("application")).isEqualToIgnoringNewLines("""
                very:
                  deep:
                    nested:
                      field:
                        that:
                          will:
                            be:
                              overridden: salut
                            but:
                              not: here
                foo:
                  bar: new_value
                  baz: to_keep
                collection:
                  items:
                  - item_C
                simple-value: kept
                something:
                  new: true""");
    }
}
