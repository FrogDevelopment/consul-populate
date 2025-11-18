package com.frogdevelopment.consul.populate.git.endpoint;

import com.frogdevelopment.consul.populate.files.ImportFileProperties;

/**
 * Lightweight summary of the current Git populator state for the management endpoint.
 */
public record GitSummary(
        Repo repo,
        Polling polling,
        ImportFileProperties files
) {
    /**
     * Repository information
     */
    public record Repo(
            String uri,
            String branch,
            String localPath,
            boolean sslVerify,
            boolean dirty,
            Head head
    ) {
    }

    /**
     * Current HEAD information
     */
    public record Head(
            String id,
            String shortId,
            String message,
            String time
    ) {
    }

    /**
     * Polling configuration
     */
    public record Polling(
            boolean enabled,
            String delay,
            String lastPullTime,
            String lastPullDuration,
            String lastPullOutcome
    ) {
    }
}
