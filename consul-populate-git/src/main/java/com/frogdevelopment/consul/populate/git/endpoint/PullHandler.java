package com.frogdevelopment.consul.populate.git.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

import io.micronaut.http.HttpResponse;

// todo: save informations like last date triggered, to be included in the GitSummary
@Slf4j
@Singleton
@RequiredArgsConstructor
 class PullHandler {

    HttpResponse<Void> handle() {
        log.info("Git polling");// todo
        return HttpResponse.accepted();
    }
}
