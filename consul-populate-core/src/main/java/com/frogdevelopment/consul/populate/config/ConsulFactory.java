package com.frogdevelopment.consul.populate.config;

import java.net.URI;

import jakarta.inject.Singleton;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;

/**
 * Factory managing {@link Vertx} classes instances for vertx-consul
 *
 * @author Le Gall Benoît
 * @since 1.0.0
 */
@Factory
public class ConsulFactory {

    static final int DEFAULT_HTTP_PORT = 80;
    static final int DEFAULT_HTTPS_PORT = 443;

    @Singleton
    @Bean(preDestroy = "close")
    Vertx vertx() {
        return Vertx.vertx();
    }

    @Singleton
    @Bean(preDestroy = "close")
    ConsulClient consulClient(final Vertx vertx, final GlobalProperties properties) {
        final ConsulClientOptions consulClientOptions;
        if (properties.getUri().isPresent()) {
            final var uri = URI.create(properties.getUri().get());
            consulClientOptions = new ConsulClientOptions()
                    .setHost(uri.getHost())
                    .setSsl("https".equalsIgnoreCase(uri.getScheme()));
            final var port = uri.getPort();
            if (port >= 0) {
                consulClientOptions.setPort(port);
            } else {
                // default port when providing a URI without port
                consulClientOptions.setPort(consulClientOptions.isSsl() ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT);
            }
        } else {
            consulClientOptions = new ConsulClientOptions()
                    .setHost(properties.getHost())
                    .setPort(properties.getPort())
                    .setSsl(properties.isSecured());
        }
        properties.getDc().ifPresent(consulClientOptions::setDc);
        properties.getAclToken().ifPresent(consulClientOptions::setAclToken);
        properties.getTimeout().ifPresent(consulClientOptions::setTimeout);
//        properties.getConnectTimeout().ifPresent(consulClientOptions::setConnectTimeout);

        return ConsulClient.create(vertx, consulClientOptions);
    }
}
