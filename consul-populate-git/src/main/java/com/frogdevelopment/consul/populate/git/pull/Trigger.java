package com.frogdevelopment.consul.populate.git.pull;

/**
 * Represents the source that triggered a git pull operation.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
public enum Trigger {
    /** Manual pull triggered via the force-pull endpoint */
    FORCED,
    /** Automatic pull triggered by the scheduled polling job */
    SCHEDULED,
    /** Pull triggered by an incoming webhook event */
    WEBHOOK
}
