package com.frogdevelopment.consul.populate;

import java.util.Map;

import io.micronaut.core.annotation.NonNull;

public interface DataImporter {

    @NonNull
    Map<String, String> execute();

}
