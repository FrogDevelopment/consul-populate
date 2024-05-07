package com.frogdevelopment.consul.populate.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

class MapHelperTest {

    @Test
    void should_mergeMaps() {
        // given
        var source = new LinkedHashMap<String, Object>();
        source.put("save", "me");
        source.put("string", "hello");
        source.put("number", 123);
        source.put("case_override_with_number", "456");
        var nestedSource = new LinkedHashMap<String, Object>();
        nestedSource.put("nested_key", "nested_value");
        source.put("nested", nestedSource);
        source.put("case_override_with_map", "azerty");

        var override = new LinkedHashMap<String, Object>();
        override.put("added", "so after all other entries in the merged result");
        override.put("string", "hello world");
        override.put("number", 987);
        override.put("case_override_with_number", 654);
        var nestedOverride = new LinkedHashMap<String, Object>();
        nestedOverride.put("nested_key_2", "nested_value_2");
        override.put("nested", nestedOverride);
        var override2 = new LinkedHashMap<String, Object>();
        override2.put("key", "value");
        override.put("case_override_with_map", override2);

        // when
        var actualMerged = MapHelper.mergeMaps(source, override);

        // then
        var expectedMerged = new LinkedHashMap<String, Object>();
        expectedMerged.put("save", "me");
        expectedMerged.put("string", "hello world");
        expectedMerged.put("number", 987);
        expectedMerged.put("case_override_with_number", 654);
        var expectedNested = new LinkedHashMap<String, Object>();
        expectedNested.put("nested_key", "nested_value");
        expectedNested.put("nested_key_2", "nested_value_2");
        expectedMerged.put("nested", expectedNested);
        var expectedOverride = new LinkedHashMap<String, Object>();
        expectedOverride.put("key", "value");
        expectedMerged.put("case_override_with_map", expectedOverride);
        expectedMerged.put("added", "so after all other entries in the merged result");

        assertThat(actualMerged).containsExactlyEntriesOf(expectedMerged);
    }

}
