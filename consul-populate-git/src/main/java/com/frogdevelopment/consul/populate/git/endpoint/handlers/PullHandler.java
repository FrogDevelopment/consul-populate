package com.frogdevelopment.consul.populate.git.endpoint.handlers;

import static com.frogdevelopment.consul.populate.git.pull.Origin.FORCED;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.git.pull.GitPull;

import io.micronaut.http.HttpResponse;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class PullHandler {

    private final GitPull gitPull;

    public HttpResponse<Void> handle() {
        gitPull.pull(FORCED);
        return HttpResponse.ok();
    }
}
