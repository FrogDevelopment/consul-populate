package com.frogdevelopment.consul.populate.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class PropertiesFilesImporterWriteTest {

    private final PropertiesFilesImporter importer = new PropertiesFilesImporter(Path.of("."), Path.of("."));

    @Test
    void should_keep_the_whitespace_at_the_end_of_the_last_value() throws IOException {
        // given - the keys are written sorted, so "zz" is the last line
        final Map<String, Object> map = Map.of("aa", "first", "zz", "ends with spaces   ");

        // when
        final var written = importer.writeValueAsString(map);

        // then - read back as a properties file, the value is the one that was written
        assertThat(load(written).getProperty("zz")).isEqualTo("ends with spaces   ");
    }

    @Test
    void should_write_special_characters_so_that_they_read_back_unchanged() throws IOException {
        // given
        final Map<String, Object> map = Map.of(
                "db.url", "jdbc:postgresql://host:5432/db?a=b&c=d",
                "path", "C:\\temp\\file",
                "multi", "line one\nline two",
                "accent", "caf\u00e9");

        // when
        final var written = importer.writeValueAsString(map);

        // then
        final var read = load(written);
        map.forEach((key, value) -> assertThat(read.getProperty(key)).isEqualTo(value));
    }

    @Test
    void should_write_the_same_output_whatever_the_order_of_the_source() throws IOException {
        // given
        final var forward = new LinkedHashMap<String, Object>();
        final var backward = new LinkedHashMap<String, Object>();
        final var keys = new String[] {"delta", "alpha", "zebra", "mid"};
        for (final var key : keys) {
            forward.put(key, "v-" + key);
        }
        for (var i = keys.length - 1; i >= 0; i--) {
            backward.put(keys[i], "v-" + keys[i]);
        }

        // when / then - stable, so a pull that changes nothing does not rewrite the same content differently
        assertThat(importer.writeValueAsString(backward)).isEqualTo(importer.writeValueAsString(forward));
        assertThat(importer.writeValueAsString(forward)).isEqualTo("alpha=v-alpha\ndelta=v-delta\nmid=v-mid\nzebra=v-zebra");
    }

    private static Properties load(final String written) throws IOException {
        final var properties = new Properties();
        properties.load(new StringReader(written));
        return properties;
    }
}
