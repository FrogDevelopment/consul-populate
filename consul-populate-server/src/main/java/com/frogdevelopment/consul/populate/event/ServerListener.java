package com.frogdevelopment.consul.populate.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import org.eclipse.jgit.api.errors.GitAPIException;

import com.frogdevelopment.consul.populate.git.GitImportJob;

import io.micronaut.context.BeanContext;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.scheduling.annotation.Async;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ServerListener {

    private final BeanContext beanContext;

    @Async
    @EventListener
    public void onServerStartupEvent(final ServerStartupEvent event) {
        final var gitImportJob = beanContext.getBean(GitImportJob.class);
        try {
            gitImportJob.init();
        } catch (final GitAPIException e) {
            throw new IllegalStateException(e);
        }
    }

    @EventListener
    public void onServerShutdownEvent(final ServerShutdownEvent event) {
        final var gitImportJob = beanContext.getBean(GitImportJob.class);

        gitImportJob.close();
    }
}
