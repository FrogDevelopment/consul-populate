package com.frogdevelopment.consul.populate.files;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.micronaut.context.annotation.Property;

@Property(name = "consul.files.format", value = "YAML")
class YamlFilesImporterTest extends BaseFilesImporterTest {

    @Language("YAML")
    static final String EXPECTED = """
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
              new: true
            """;

    @Inject
    private YamlFilesImporter filesImporter;

    @ParameterizedTest
    @CsvSource({
            "json, false",
            "yaml, true",
            "yml, true",
            "YAML, true",
            "YML, true",
            "properties, false",
    })
    void should_return_expectedValue(final String extension, final boolean expected) {
        // when
        final var actual = filesImporter.isExtensionAccepted(extension);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void should_mergeYaml() {
        // when
        final var result = filesImporter.execute();

        // then
        assertThat(result)
                .hasSize(1)
                .containsEntry("application", EXPECTED);
    }
}
