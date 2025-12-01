package com.frogdevelopment.consul.populate.git.endpoint.model;

import com.frogdevelopment.consul.populate.files.ImportFileProperties;
import com.frogdevelopment.consul.populate.git.pull.Status;
import com.frogdevelopment.consul.populate.git.pull.Trigger;

/**
 * Lightweight summary of the current Git populator state for the management endpoint.
 *
 * @param repo  repository information including URI, branch, and HEAD commit
 * @param pull  pull configuration and last pull statistics
 * @param files file import configuration settings
 * @author Le Gall Benoît
 * @since 1.2.0
 */
public record GitSummary(
        Repo repo,
        Pull pull,
        ImportFileProperties files
) {
    /**
     * Repository information including connection details and current state.
     *
     * @param uri       the repository URI (with credentials masked)
     * @param branch    the tracked branch name
     * @param localPath the local directory path where the repository is cloned
     * @param dirty     whether the working directory has uncommitted changes
     * @param head      the current HEAD commit information, or null if unavailable
     */
    public record Repo(
            String uri,
            String branch,
            String localPath,
            boolean dirty,
            Head head
    ) {
    }

    /**
     * Current HEAD commit information.
     *
     * @param id       the full commit SHA
     * @param shortId  the abbreviated commit SHA (7 characters)
     * @param message  the commit's short message (first line)
     * @param time     the commit timestamp as an ISO-8601 string
     */
    public record Head(
            String id,
            String shortId,
            String message,
            String time
    ) {
    }

    /**
     * Pull configuration and statistics.
     *
     * @param scheduled        whether scheduled polling is enabled
     * @param scheduledInterval the polling interval as an ISO-8601 duration string, or null if disabled
     * @param trigger          the source of the last pull operation
     * @param lastPullTime     the timestamp of the last pull as an ISO-8601 string
     * @param lastPullDuration the duration of the last pull formatted as "ss.SSS's"
     * @param lastPullOutcome  the outcome status of the last pull
     */
    public record Pull(
            boolean scheduled,
            String scheduledInterval,
            Trigger trigger,
            String lastPullTime,
            String lastPullDuration,
            Status lastPullOutcome
    ) {
    }
}
