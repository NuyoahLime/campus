package com.campusguinness.identity.application.port;

/**
 * Query port for checking user table state during bootstrap.
 * Separate from domain UserRepository — this is an operational concern.
 */
public interface UserBootstrapStateQuery {

    /**
     * @return total number of rows in the users table
     */
    long countUsers();
}
