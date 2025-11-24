package com.frogdevelopment.consul.populate.git.endpoint.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.git.GitProperties;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class PollingHandler {

    private final GitProperties gitProperties;

    public HttpResponse<Void> handle(final HttpRequest<?> request) {
        final var enable = request.getParameters().get("enable", Boolean.class);
        if (enable.isPresent()){
            log.warn("Git polling: {}", enable);
            gitProperties.getPolling().setEnabled(enable.get()); // todo take into account change in the job ?
            return HttpResponse.ok();
        }else {
            return HttpResponse.notModified();
        }
    }
}
