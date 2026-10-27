package com.frogdevelopment.consul.populate;

import static com.frogdevelopment.consul.populate.VertxUtils.toBlocking;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.frogdevelopment.consul.populate.config.GlobalProperties;

import io.vertx.core.Vertx;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;

/**
 * Against a real Consul, whose transactions are limited to 64 operations and to a 524288 bytes body (413 above).
 */
@Testcontainers
class PopulateLargeValuesIntegrationTest {

    @Container
    private static final ConsulContainer CONSUL = new ConsulContainer("hashicorp/consul:1.22.7");

    private final Vertx vertx = Vertx.vertx();
    private ConsulClient consulClient;
    private Map<String, String> source;
    private PopulateServiceImpl populateService;

    @BeforeEach
    void setUp() {
        consulClient = ConsulClient.create(vertx, new ConsulClientOptions()
                .setHost(CONSUL.getHost())
                .setPort(CONSUL.getMappedPort(8500)));
        toBlocking(consulClient.deleteValues(""));

        source = new LinkedHashMap<>();
        populateService = new PopulateServiceImpl(consulClient, new GlobalProperties(), () -> source);
    }

    @AfterEach
    void tearDown() {
        consulClient.close();
        vertx.close();
    }

    @Test
    void should_split_values_that_do_not_fit_in_one_transaction() {
        // given - six values of about 335 KB once encoded: together they are far above the limit of one transaction
        for (var i = 0; i < 6; i++) {
            source.put("big-" + i, ("value-" + i + "-").repeat(1).concat("x".repeat(250_000)));
        }

        // when
        populateService.populate(false);

        // then - every value arrived, intact
        for (var i = 0; i < 6; i++) {
            final var stored = toBlocking(consulClient.getValue("config/big-" + i));
            assertThat(stored.getValue()).isEqualTo(source.get("big-" + i));
        }
    }

    @Test
    void should_refuse_a_value_that_can_never_fit_without_writing_anything() {
        // given - about 533 KB once encoded, above the 524288 bytes Consul accepts; a small key comes first
        source.put("a-small-key", "value");
        source.put("b-too-large", "x".repeat(400_000));

        // when
        final var caught = catchThrowable(() -> populateService.populate(false));

        // then
        assertThat(caught)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("config/b-too-large");
        assertThat(toBlocking(consulClient.getKeys("config/"))).isEmpty();
    }

    @Test
    void should_accept_the_largest_value_that_fits() {
        // given - 393000 characters are about 524 KB once base64 encoded, just under the limit with the key and the verb
        source.put("just-fits", "x".repeat(393_000));

        // when
        populateService.populate(false);

        // then
        assertThat(toBlocking(consulClient.getValue("config/just-fits")).getValue()).hasSize(393_000);
    }
}
