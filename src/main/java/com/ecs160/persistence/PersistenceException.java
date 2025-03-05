package com.ecs160.persistence;

/*
 * PersistenceException extends Exception and not RuntimeException so that we force the exception to be caught.
 * PersistenceException is a base exception type for any exceptions related to persistence
 */
public class PersistenceException extends Exception {
    /**
     * Construct new instance of PersistenceException
     * @param message message for why PersistableException is thrown
     */
    public PersistenceException(String message) {
        super(message);
    }
}
