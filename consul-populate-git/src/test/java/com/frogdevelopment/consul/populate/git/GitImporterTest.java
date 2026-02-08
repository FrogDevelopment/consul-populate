package com.frogdevelopment.consul.populate.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockConstruction;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frogdevelopment.consul.populate.files.ImportFileProperties;
import com.frogdevelopment.consul.populate.files.JsonFilesImporter;
import com.frogdevelopment.consul.populate.files.PropertiesFilesImporter;
import com.frogdevelopment.consul.populate.files.YamlFilesImporter;

@ExtendWith(MockitoExtension.class)
class GitImporterTest {

    @Mock
    private RepositoryDirectoryProvider repositoryDirectoryProvider;
    @Mock
    private GitProperties gitProperties;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GitImporter gitImporter;

    @Test
    void shouldUseRepositoryDirectoryAsRoot_whenRootPathIsEmpty() {
        // given
        var repositoryDirectory = Path.of("/tmp/repo");
        var fileProperties = new ImportFileProperties();
        fileProperties.setRootPath("");
        fileProperties.setTarget("");
        fileProperties.setFormat(ImportFileProperties.Format.YAML);

        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDirectory);
        given(gitProperties.getFileProperties()).willReturn(fileProperties);

        try (var yamlMock = mockConstruction(YamlFilesImporter.class, (mock, context) -> {
            assertThat(context.arguments()).hasSize(2);
            assertThat(context.arguments().get(0)).isEqualTo(repositoryDirectory);
            assertThat(context.arguments().get(1)).isEqualTo(repositoryDirectory);
            given(mock.execute()).willReturn(Map.of("key", "value"));
        })) {
            // when
            var result = gitImporter.execute();

            // then
            assertThat(result).containsEntry("key", "value");
            assertThat(yamlMock.constructed()).hasSize(1);
        }
    }

    @Test
    void shouldUseRepositoryDirectoryAsRoot_whenRootPathIsNull() {
        // given
        var repositoryDirectory = Path.of("/tmp/repo");
        var fileProperties = new ImportFileProperties();
        fileProperties.setRootPath(null);
        fileProperties.setTarget(null);
        fileProperties.setFormat(ImportFileProperties.Format.YAML);

        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDirectory);
        given(gitProperties.getFileProperties()).willReturn(fileProperties);

        try (var yamlMock = mockConstruction(YamlFilesImporter.class, (mock, context) -> {
            assertThat(context.arguments()).hasSize(2);
            assertThat(context.arguments().get(0)).isEqualTo(repositoryDirectory);
            assertThat(context.arguments().get(1)).isEqualTo(repositoryDirectory);
            given(mock.execute()).willReturn(Map.of());
        })) {
            // when
            gitImporter.execute();

            // then
            assertThat(yamlMock.constructed()).hasSize(1);
        }
    }

    @Test
    void shouldResolveRootPath_whenRootPathIsProvided() {
        // given
        var repositoryDirectory = Path.of("/tmp/repo");
        var fileProperties = new ImportFileProperties();
        fileProperties.setRootPath("config");
        fileProperties.setTarget("");
        fileProperties.setFormat(ImportFileProperties.Format.YAML);

        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDirectory);
        given(gitProperties.getFileProperties()).willReturn(fileProperties);

        try (var yamlMock = mockConstruction(YamlFilesImporter.class, (mock, context) -> {
            assertThat(context.arguments()).hasSize(2);
            assertThat(context.arguments().get(0)).isEqualTo(repositoryDirectory.resolve("config"));
            assertThat(context.arguments().get(1)).isEqualTo(repositoryDirectory.resolve("config"));
            given(mock.execute()).willReturn(Map.of());
        })) {
            // when
            gitImporter.execute();

            // then
            assertThat(yamlMock.constructed()).hasSize(1);
        }
    }

    @Test
    void shouldResolveTargetPath_whenTargetIsProvided() {
        // given
        var repositoryDirectory = Path.of("/tmp/repo");
        var fileProperties = new ImportFileProperties();
        fileProperties.setRootPath("config");
        fileProperties.setTarget("dev");
        fileProperties.setFormat(ImportFileProperties.Format.YAML);

        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDirectory);
        given(gitProperties.getFileProperties()).willReturn(fileProperties);

        try (var yamlMock = mockConstruction(YamlFilesImporter.class, (mock, context) -> {
            assertThat(context.arguments()).hasSize(2);
            assertThat(context.arguments().get(0)).isEqualTo(repositoryDirectory.resolve("config"));
            assertThat(context.arguments().get(1)).isEqualTo(repositoryDirectory.resolve("config/dev"));
            given(mock.execute()).willReturn(Map.of());
        })) {
            // when
            gitImporter.execute();

            // then
            assertThat(yamlMock.constructed()).hasSize(1);
        }
    }

    @Test
    void shouldDelegateToJsonImporter_whenFormatIsJson() {
        // given
        var repositoryDirectory = Path.of("/tmp/repo");
        var fileProperties = new ImportFileProperties();
        fileProperties.setRootPath("");
        fileProperties.setTarget("");
        fileProperties.setFormat(ImportFileProperties.Format.JSON);

        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDirectory);
        given(gitProperties.getFileProperties()).willReturn(fileProperties);

        try (var jsonMock = mockConstruction(JsonFilesImporter.class, (mock, context) -> {
            assertThat(context.arguments()).hasSize(3);
            assertThat(context.arguments().get(2)).isSameAs(objectMapper);
            given(mock.execute()).willReturn(Map.of("json-key", "json-value"));
        })) {
            // when
            var result = gitImporter.execute();

            // then
            assertThat(result).containsEntry("json-key", "json-value");
            assertThat(jsonMock.constructed()).hasSize(1);
        }
    }

    @Test
    void shouldDelegateToPropertiesImporter_whenFormatIsProperties() {
        // given
        var repositoryDirectory = Path.of("/tmp/repo");
        var fileProperties = new ImportFileProperties();
        fileProperties.setRootPath("");
        fileProperties.setTarget("");
        fileProperties.setFormat(ImportFileProperties.Format.PROPERTIES);

        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDirectory);
        given(gitProperties.getFileProperties()).willReturn(fileProperties);

        try (var propsMock = mockConstruction(PropertiesFilesImporter.class, (mock, context) -> {
            assertThat(context.arguments()).hasSize(2);
            given(mock.execute()).willReturn(Map.of("prop-key", "prop-value"));
        })) {
            // when
            var result = gitImporter.execute();

            // then
            assertThat(result).containsEntry("prop-key", "prop-value");
            assertThat(propsMock.constructed()).hasSize(1);
        }
    }

    @Test
    void shouldDelegateToYamlImporter_whenFormatIsYaml() {
        // given
        var repositoryDirectory = Path.of("/tmp/repo");
        var fileProperties = new ImportFileProperties();
        fileProperties.setRootPath("");
        fileProperties.setTarget("");
        fileProperties.setFormat(ImportFileProperties.Format.YAML);

        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDirectory);
        given(gitProperties.getFileProperties()).willReturn(fileProperties);

        try (var yamlMock = mockConstruction(YamlFilesImporter.class, (mock, context) -> {
            assertThat(context.arguments()).hasSize(2);
            given(mock.execute()).willReturn(Map.of("yaml-key", "yaml-value"));
        })) {
            // when
            var result = gitImporter.execute();

            // then
            assertThat(result).containsEntry("yaml-key", "yaml-value");
            assertThat(yamlMock.constructed()).hasSize(1);
        }
    }

    @Test
    void shouldHandleComplexPaths() {
        // given
        var repositoryDirectory = Path.of("/tmp/consul-populate-abc123/my-repo");
        var fileProperties = new ImportFileProperties();
        fileProperties.setRootPath("src/main/resources");
        fileProperties.setTarget("config/production");
        fileProperties.setFormat(ImportFileProperties.Format.JSON);

        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDirectory);
        given(gitProperties.getFileProperties()).willReturn(fileProperties);

        try (var jsonMock = mockConstruction(JsonFilesImporter.class, (mock, context) -> {
            var expectedRoot = repositoryDirectory.resolve("src/main/resources");
            var expectedTarget = repositoryDirectory.resolve("src/main/resources/config/production");
            assertThat(context.arguments().get(0)).isEqualTo(expectedRoot);
            assertThat(context.arguments().get(1)).isEqualTo(expectedTarget);
            given(mock.execute()).willReturn(Map.of());
        })) {
            // when
            gitImporter.execute();

            // then
            assertThat(jsonMock.constructed()).hasSize(1);
        }
    }

    @Test
    void shouldReturnImportedData() {
        // given
        var repositoryDirectory = Path.of("/tmp/repo");
        var fileProperties = new ImportFileProperties();
        fileProperties.setRootPath("");
        fileProperties.setTarget("");
        fileProperties.setFormat(ImportFileProperties.Format.YAML);

        var expectedData = Map.of(
                "database/host", "localhost",
                "database/port", "5432",
                "app/name", "consul-populate"
        );

        given(repositoryDirectoryProvider.getRepository()).willReturn(repositoryDirectory);
        given(gitProperties.getFileProperties()).willReturn(fileProperties);

        try (var yamlMock = mockConstruction(YamlFilesImporter.class, (mock, context) -> {
            given(mock.execute()).willReturn(expectedData);
        })) {
            // when
            var result = gitImporter.execute();

            // then
            assertThat(result)
                    .hasSize(3)
                    .containsEntry("database/host", "localhost")
                    .containsEntry("database/port", "5432")
                    .containsEntry("app/name", "consul-populate");
        }
    }
}
