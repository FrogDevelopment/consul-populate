package com.frogdevelopment.consul.populate.git;

import lombok.RequiredArgsConstructor;

import java.util.Map;
import jakarta.inject.Singleton;

import com.frogdevelopment.consul.populate.DataImporter;

@Singleton
@RequiredArgsConstructor
public class GitImporter implements DataImporter {

    @Override
    public Map<String, String> execute() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
