package com.frogdevelopment.consul.populate.git;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import io.micronaut.core.annotation.Blocking;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.scheduling.annotation.Async;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ServerEventsListener {

    private final GitImportJob gitImportJob;

    @Async
    @EventListener
    public void onServerStartupEvent(final ServerStartupEvent ignored) {
        gitImportJob.start();
    }

    @Blocking
    @EventListener
    public void onServerShutdownEvent(final ServerShutdownEvent ignored) {
        gitImportJob.stop();
    }
}
