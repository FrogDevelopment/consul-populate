package com.frogdevelopment.consul.populate.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;

@ExtendWith(MockitoExtension.class)
class ConsulFactoryTest {

    @InjectMocks
    private ConsulFactory  consulFactory;

    @Mock
    private Vertx vertx;
    @Mock
    private GlobalProperties properties;
    @Mock
    private ConsulClient consulClient;

    @Captor
    private ArgumentCaptor<ConsulClientOptions> consulClientOptionsCaptor;

    @Test
    void should_use_uri_with_port() {
        // given
        try(var stub = Mockito.mockStatic(ConsulClient.class)) {
            stub.when(()-> ConsulClient.create(eq(vertx), consulClientOptionsCaptor.capture()))
                    .thenReturn(consulClient);
            given(properties.getUri()).willReturn(Optional.of("http://my-domain:8666"));

            // when
            consulFactory.consulClient(vertx, properties);

            // then
            final var consulClientOptions = consulClientOptionsCaptor.getValue();
            assertThat(consulClientOptions.getHost()).isEqualTo("my-domain");
            assertThat(consulClientOptions.getPort()).isEqualTo(8666);
            assertThat(consulClientOptions.isSsl()).isFalse();
        }
    }

    @Test
    void should_use_uri_without_port() {
        // given
        try(var stub = Mockito.mockStatic(ConsulClient.class)) {
            stub.when(()-> ConsulClient.create(eq(vertx), consulClientOptionsCaptor.capture()))
                    .thenReturn(consulClient);
            given(properties.getUri()).willReturn(Optional.of("http://my-domain"));

            // when
            consulFactory.consulClient(vertx, properties);

            // then
            final var consulClientOptions = consulClientOptionsCaptor.getValue();
            assertThat(consulClientOptions.getHost()).isEqualTo("my-domain");
            assertThat(consulClientOptions.getPort()).isEqualTo(80);
            assertThat(consulClientOptions.isSsl()).isFalse();
        }
    }

    @Test
    void should_use_host_and_port() {
        // given
        try(var stub = Mockito.mockStatic(ConsulClient.class)) {
            stub.when(()-> ConsulClient.create(eq(vertx), consulClientOptionsCaptor.capture()))
                    .thenReturn(consulClient);
            given(properties.getUri()).willReturn(Optional.empty());
            given(properties.getHost()).willReturn("foo");
            given(properties.getPort()).willReturn(1234);
            given(properties.isSecured()).willReturn(true);

            // when
            consulFactory.consulClient(vertx, properties);

            // then
            final var consulClientOptions = consulClientOptionsCaptor.getValue();
            assertThat(consulClientOptions.getHost()).isEqualTo("foo");
            assertThat(consulClientOptions.getPort()).isEqualTo(1234);
            assertThat(consulClientOptions.isSsl()).isTrue();
        }
    }

}
