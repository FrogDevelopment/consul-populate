package com.frogdevelopment.consul.populate.git.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.PopulateService;
import com.frogdevelopment.consul.populate.git.GitProperties;
import com.frogdevelopment.consul.populate.git.pull.GitPullJob;

import io.micronaut.core.annotation.Blocking;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import io.micronaut.scheduling.annotation.Async;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ServerEventsListener {

    private final PopulateService populateService;
    private final GitProperties gitProperties;
    private final GitPullJob gitPullJob;

    @Async
    @EventListener
    public void onServerStartupEvent(final ServerStartupEvent ignored) {
        log.info("Populating Consul with repository");
        populateService.populate();

        if (gitProperties.getPolling().isEnabled()) {
            gitPullJob.start();
        }
    }

    @Blocking
    @EventListener
    public void onServerShutdownEvent(final ServerShutdownEvent ignored) {
        if (gitProperties.getPolling().isEnabled()) {
            gitPullJob.stop();
        }
    }
}
