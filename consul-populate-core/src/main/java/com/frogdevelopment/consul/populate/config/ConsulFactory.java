package com.frogdevelopment.consul.populate.config;

import jakarta.inject.Singleton;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;

@Factory
public class ConsulFactory {

    @Singleton
    @Bean(preDestroy = "close")
    Vertx vertx() {
        return Vertx.vertx();
    }

    @Singleton
    @Bean(preDestroy = "close")
    ConsulClient consulClient(final Vertx vertx, final GlobalProperties properties) {
        final var consulClientOptions = new ConsulClientOptions()
                .setHost(properties.getHost())
                .setPort(properties.getPort())
                .setSsl(properties.isSecured());
        properties.getDc().ifPresent(consulClientOptions::setDc);
        properties.getAclToken().ifPresent(consulClientOptions::setAclToken);
        properties.getTimeout().ifPresent(consulClientOptions::setTimeout);

        return ConsulClient.create(vertx, consulClientOptions);
    }
}
