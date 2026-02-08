package com.frogdevelopment.consul.populate.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.io.File;
import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
 class FileImporterTest {

    private YamlFilesImporter  yamlFilesImporter;

    @Mock
    private Path rootPath;
    @Mock
    private Path targetPath;
    @Mock
    private File file;

    @BeforeEach()
    void beforeEach() {
        yamlFilesImporter = new YamlFilesImporter(rootPath, targetPath);
    }

    @Test
    void should_throwAnException_when_rootPathDoesNotExist() {
        // given
        given(rootPath.toFile()).willReturn(file);
        given(rootPath.toString()).willReturn("root-mocked-file");
        given(file.exists()).willReturn(false);

        // when
        var caught = Assertions.catchThrowable(() -> yamlFilesImporter.execute());

        // then
        assertThat(caught)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Root directory does not exist: root-mocked-file");
    }

    @Test
    void should_throwAnException_when_targetPathDoesNotExist() {
        // given
        given(rootPath.toFile()).willReturn(file);
        given(targetPath.toFile()).willReturn(file);
        given(targetPath.toString()).willReturn("target-mocked-file");
        given(file.exists())
                .willReturn(true)
                .willReturn(false);

        // when
        var caught = Assertions.catchThrowable(() -> yamlFilesImporter.execute());

        // then
        assertThat(caught)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Target directory does not exist: target-mocked-file");
    }

}
