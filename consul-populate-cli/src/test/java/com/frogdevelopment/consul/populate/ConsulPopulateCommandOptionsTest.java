package com.frogdevelopment.consul.populate;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.frogdevelopment.consul.populate.config.GlobalProperties;
import com.frogdevelopment.consul.populate.files.ImportFileProperties;

import picocli.CommandLine.Option;

/**
 * Picocli rejects every option it does not know, so a property that exists in the configuration classes but has no
 * option in {@link ConsulPopulateCommand} cannot be passed as an argument. This test keeps both in sync.
 */
class ConsulPopulateCommandOptionsTest {

    @Test
    void every_configuration_property_can_be_passed_as_an_argument() {
        // given
        var declaredOptions = Arrays.stream(ConsulPopulateCommand.class.getDeclaredFields())
                .map(field -> field.getAnnotation(Option.class))
                .filter(Objects::nonNull)
                .flatMap(option -> Arrays.stream(option.names()))
                .collect(Collectors.toSet());

        // when
        var expectedOptions = Stream.of(
                        propertiesOf(GlobalProperties.class, "--consul."),
                        propertiesOf(GlobalProperties.KV.class, "--consul.kv."),
                        propertiesOf(ImportFileProperties.class, "--consul.files."))
                .flatMap(Set::stream)
                // `kv` is the nested KV object: its own properties are listed with the `--consul.kv.` prefix
                .filter(option -> !option.equals("--consul.kv"))
                .collect(Collectors.toSet());

        // then
        assertThat(declaredOptions).containsAll(expectedOptions);
    }

    private static Set<String> propertiesOf(final Class<?> type, final String prefix) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
                .map(Field::getName)
                .map(ConsulPopulateCommandOptionsTest::toKebabCase)
                .map(name -> prefix + name)
                .collect(Collectors.toSet());
    }

    private static String toKebabCase(final String camelCase) {
        return camelCase.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}
