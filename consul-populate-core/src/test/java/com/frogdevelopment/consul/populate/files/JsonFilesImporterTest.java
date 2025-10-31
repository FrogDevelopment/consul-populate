package com.frogdevelopment.consul.populate.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TreeMap;

import jakarta.inject.Inject;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.micronaut.context.annotation.Property;

@Property(name = "consul.files.format", value = "JSON")
class JsonFilesImporterTest extends BaseFilesImporterTest {

    @Language("JSON")
    private static final String EXPECTED = """
            {
              "very" : {
                "deep" : {
                  "nested" : {
                    "field" : {
                      "that" : {
                        "will" : {
                          "be" : {
                            "overridden" : "salut"
                          },
                          "but" : {
                            "not" : "here"
                          }
                        }
                      }
                    }
                  }
                }
              },
              "foo" : {
                "bar" : "new_value",
                "baz" : "to_keep"
              },
              "collections" : {
                "items" : [ "item_C" ]
              },
              "simple-value" : "kept",
              "something" : {
                "new" : true
              }
            }""";

    @Inject
    private JsonFilesImporter filesImporter;

    @ParameterizedTest
    @CsvSource({
            "JSON, true",
            "json, true",
            "yaml, false",
            "properties, false",
    })
    void should_return_expectedValue(final String extension, final boolean expected) {
        // when
        final var actual = filesImporter.isExtensionAccepted(extension);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void should_mergeJson() {
        // when
        final var result = filesImporter.execute();

        // then
        assertThat(result)
                .hasSize(1)
                .containsEntry("application", EXPECTED);
    }

    @Test
    void should_handle_empty() throws JsonProcessingException {
        // given
        // when
        final var value = filesImporter.writeValueAsString(new TreeMap<>());

        // then
        assertThat(value).isBlank();
    }
}
