package com.frogdevelopment.consul.populate.git;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.inject.Singleton;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frogdevelopment.consul.populate.DataImporter;
import com.frogdevelopment.consul.populate.PopulateService;
import com.frogdevelopment.consul.populate.files.ImportFileProperties;
import com.frogdevelopment.consul.populate.files.JsonFilesImporter;
import com.frogdevelopment.consul.populate.files.PropertiesFilesImporter;
import com.frogdevelopment.consul.populate.files.YamlFilesImporter;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;

@Factory
@Requires(property = "consul.git")
@RequiredArgsConstructor
public class GitImportFactory {

    private final BeanContext beanContext;

    @Singleton
    public GitImportJob createGitImportJob(final GitProperties gitProperties){
        final var credentialsProvider = createCredentialsProvider(gitProperties);
        CredentialsProvider.setDefault(credentialsProvider);

        final var repositoryDirectory = createRepositoryDirectory(gitProperties);

        final var dataImporter = createDataImporter(repositoryDirectory, gitProperties.getFileProperties());
        beanContext.registerSingleton(DataImporter.class, dataImporter);

        final var populateService = beanContext.getBean(PopulateService.class);
        final var taskScheduler = beanContext.getBean(TaskScheduler.class, Qualifiers.byName(TaskExecutors.SCHEDULED));

        return new GitImportJob(gitProperties, repositoryDirectory, populateService, taskScheduler);
    }

    private static CredentialsProvider createCredentialsProvider(final GitProperties gitProperties) {
        final var token = gitProperties.getToken();
        if (StringUtils.isNotEmpty(token)) {
            return new UsernamePasswordCredentialsProvider(token, "");
        } else {
            return new UsernamePasswordCredentialsProvider(gitProperties.getUsername(), gitProperties.getPassword());
        }
    }

    private static Path createRepositoryDirectory(final GitProperties gitProperties) {
        try {
            final var localPath = gitProperties.getLocalPath();
            if (Files.notExists(localPath)) {
                Files.createDirectories(localPath);
            }
            final var tmpDirectory = Files.createTempDirectory(localPath, "consul-populate-");

            final var urIish = new URIish(gitProperties.getUri());

            return tmpDirectory.resolve(urIish.getHumanishName());
        } catch (final IOException | URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private DataImporter createDataImporter(final Path repositoryDirectory, final ImportFileProperties fileProperties) {

        final var rootPath = StringUtils.isEmpty(fileProperties.getRootPath())
                ? repositoryDirectory
                : repositoryDirectory.resolve(fileProperties.getRootPath());
        final var targetPath = StringUtils.isEmpty(fileProperties.getTarget())
                ? rootPath
                : rootPath.resolve(fileProperties.getTarget());

        return switch (fileProperties.getFormat()) {
            case JSON -> new JsonFilesImporter(rootPath, targetPath, beanContext.getBean(ObjectMapper.class));
            case PROPERTIES -> new PropertiesFilesImporter(rootPath, targetPath);
            case YAML -> new YamlFilesImporter(rootPath, targetPath);
        };
    }
}
