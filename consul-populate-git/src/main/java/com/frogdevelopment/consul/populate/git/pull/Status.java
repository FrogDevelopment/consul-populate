package com.frogdevelopment.consul.populate.git.pull;

/**
 * Represents the outcome of a git pull operation.
 *
 * @author Le Gall Benoît
 * @since 1.2.0
 */
public enum Status {
    /** Pull completed successfully */
    SUCCESS,
    /** Pull failed or encountered an error */
    FAILURE
}
