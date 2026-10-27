package com.frogdevelopment.consul.populate.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * What the importers do with files that are wrong, empty, ambiguous or that point outside of the directory.
 */
class FilesImporterRobustnessTest {

    @TempDir
    Path tempDir;

    @Nested
    class MalformedFiles {

        @Test
        void should_name_the_file_when_a_yaml_file_is_not_a_mapping() throws IOException {
            // given
            write("list.yaml", "- one\n- two\n");

            // when
            final var caught = catchThrowable(() -> yaml().execute());

            // then - not a bare ClassCastException
            assertThat(caught)
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("list.yaml")
                    .hasMessageContaining("must be a mapping");
        }

        @Test
        void should_name_the_file_when_a_yaml_file_has_a_syntax_error() throws IOException {
            // given
            write("broken.yaml", "key: [unclosed\nother: value\n");

            // when
            final var caught = catchThrowable(() -> yaml().execute());

            // then
            assertThat(caught)
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid YAML in")
                    .hasMessageContaining("broken.yaml");
        }

        @Test
        void should_name_the_file_when_a_json_file_is_not_an_object() throws IOException {
            // given
            write("list.json", "[1, 2]");

            // when
            final var caught = catchThrowable(() -> json().execute());

            // then
            assertThat(caught)
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid JSON in")
                    .hasMessageContaining("list.json");
        }

        @Test
        void should_name_the_file_when_a_properties_file_has_a_bad_escape() throws IOException {
            // given
            write("bad.properties", "key=\\uZZZZ\n");

            // when
            final var caught = catchThrowable(() -> properties().execute());

            // then
            assertThat(caught)
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid properties in")
                    .hasMessageContaining("bad.properties");
        }
    }

    @Nested
    class EmptyFiles {

        @Test
        void should_skip_an_empty_json_file_like_an_empty_yaml_file() throws IOException {
            // given
            write("empty.json", "  \n");
            write("kept.json", "{\"a\": 1}");

            // when
            final var result = json().execute();

            // then
            assertThat(result).containsOnlyKeys("kept");
        }

        @Test
        void should_refuse_to_import_nothing_because_it_would_delete_everything_under_the_path() throws IOException {
            // given - every file is empty, for instance after a bad commit
            write("one.yaml", "");
            write("two.yaml", "# only a comment\n");

            // when
            final var caught = catchThrowable(() -> yaml().execute());

            // then
            assertThat(caught)
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nothing to import");
        }
    }

    @Nested
    class CollidingFiles {

        @Test
        void should_refuse_two_files_that_map_to_the_same_key() throws IOException {
            // given
            write("application.yaml", "from: yaml\n");
            write("application.yml", "from: yml\n");

            // when
            final var caught = catchThrowable(() -> yaml().execute());

            // then - it names both files and the key, whatever the order they were listed in
            assertThat(caught)
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'application.yaml' and 'application.yml'")
                    .hasMessageContaining("'application'");
        }
    }

    @Nested
    class SymbolicLinks {

        private Path repository;
        private Path outside;

        void setUpLinks() throws IOException {
            repository = Files.createDirectories(tempDir.resolve("repository"));
            outside = Files.createDirectories(tempDir.resolve("outside"));
            Files.writeString(repository.resolve("application.yaml"), "from: repository\n");
            Files.writeString(outside.resolve("secret.yaml"), "from: outside\n");
            try {
                Files.createSymbolicLink(repository.resolve("alias.yaml"), Path.of("application.yaml"));
                Files.createSymbolicLink(repository.resolve("stolen.yaml"), Path.of("../outside/secret.yaml"));
                Files.createSymbolicLink(repository.resolve("dangling.yaml"), Path.of("nowhere.yaml"));
            } catch (final UnsupportedOperationException | IOException e) {
                assumeTrue(false, "symbolic links are not available here: " + e);
            }
        }

        @Test
        void should_ignore_the_links_that_point_outside_when_confined() throws IOException {
            // given
            setUpLinks();
            final var importer = new YamlFilesImporter(repository, repository);
            importer.confineTo(repository);

            // when
            final var result = importer.execute();

            // then - the file and the link that stays inside are read; the link to the outside, and the broken one, are not
            assertThat(result).containsOnlyKeys("application", "alias");
            assertThat(result.get("alias")).contains("from: repository");
        }

        @Test
        void should_follow_every_link_by_default_as_a_kubernetes_config_map_needs() throws IOException {
            // given - a ConfigMap volume is made of links to ..data/<file>, which are not inside the directory itself
            setUpLinks();
            final var importer = new YamlFilesImporter(repository, repository);

            // when - the broken link is the only thing that cannot be read
            final var caught = catchThrowable(importer::execute);

            // then
            assertThat(caught).isInstanceOf(RuntimeException.class);
            Files.delete(repository.resolve("dangling.yaml"));
            assertThat(importer.execute()).containsOnlyKeys("application", "alias", "stolen");
        }
    }

    private void write(final String name, final String content) throws IOException {
        Files.writeString(tempDir.resolve(name), content);
    }

    private YamlFilesImporter yaml() {
        return new YamlFilesImporter(tempDir, tempDir);
    }

    private JsonFilesImporter json() {
        return new JsonFilesImporter(tempDir, tempDir, new ObjectMapper());
    }

    private PropertiesFilesImporter properties() {
        return new PropertiesFilesImporter(tempDir, tempDir);
    }
}
