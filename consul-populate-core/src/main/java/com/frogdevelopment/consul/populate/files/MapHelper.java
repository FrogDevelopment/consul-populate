package com.frogdevelopment.consul.populate.files;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;

import io.micronaut.core.annotation.NonNull;

/**
 * Helper to merge maps
 *
 * @author Le Gall Benoît
 * @since 1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MapHelper {

    /**
     * Merging maps
     *
     * @param source   Source to be merged
     * @param override Data use to merge
     * @return the merged result
     */
    static @NonNull Map<String, SequencedMap<String, Object>> merge(
            @NonNull final Map<String, SequencedMap<String, Object>> source,
            @NonNull final Map<String, SequencedMap<String, Object>> override) {
        final var merged = new LinkedHashMap<>(source);
        override.forEach((key, overridingValue) -> {
            final var sourceValue = source.get(key);
            if (sourceValue != null) {
                merged.put(key, mergeMaps(sourceValue, overridingValue));
            } else {
                merged.put(key, overridingValue);
            }
        });

        return merged;
    }

    /**
     *
     * @param source Source to be merged
     * @param override Data use to merge
     * @return the merged map
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static @NonNull SequencedMap<String, Object> mergeMaps(@NonNull final Map<String, Object> source,
                                                           @NonNull final Map<String, Object> override) {
        final var merged = new LinkedHashMap<>(source);
        override.forEach((key, overridingValue) -> {
            final var sourceValue = source.get(key);
            if (sourceValue != null) {
                if (sourceValue instanceof final Map sourceAsMap && overridingValue instanceof final Map overrideAsMap) {
                    merged.put(key, mergeMaps(sourceAsMap, overrideAsMap));
                } else {
                    merged.put(key, overridingValue);
                }
            } else {
                merged.put(key, overridingValue);
            }
        });

        return merged;
    }
}
