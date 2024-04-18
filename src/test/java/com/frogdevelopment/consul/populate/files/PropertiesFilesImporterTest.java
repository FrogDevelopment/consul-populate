package com.frogdevelopment.consul.populate.files;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.micronaut.context.annotation.Property;

@Property(name = "consul.files.format", value = "PROPERTIES")
class PropertiesFilesImporterTest extends BaseFilesImporterTest {

    @Language("PROPERTIES")
    private static final String EXPECTED = """
            collection.items=item_C
            something.new=true
            very.deep.nested.field.that.will.but.not=here
            very.deep.nested.field.that.will.be.overridden=salut
            foo.bar=new_value
            foo.baz=to_keep
            simple-value=kept""";

    @Inject
    private PropertiesFilesImporter filesImporter;

    @ParameterizedTest
    @CsvSource({
            "json, false",
            "yaml, false",
            "properties, true",
            "PROPERTIES, true",
    })
    void should_return_expectedValue(final String extension, final boolean expected) {
        // when
        final var actual = filesImporter.isExtensionAccepted(extension);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void should_mergeProperties() {
        // when
        final var result = filesImporter.execute();

        // then
        assertThat(result)
                .hasSize(1)
                .containsEntry("application", EXPECTED);
    }

}
