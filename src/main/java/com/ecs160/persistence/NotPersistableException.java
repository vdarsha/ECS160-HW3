package com.ecs160.persistence;

/*
 * Exception thrown if a given class cannot be persisted
 */
public class NotPersistableException extends PersistenceException {
    /**
     * Construct new instance of NotPersistableException
     * @param message message for why NotPersistableException is thrown
     */
    public NotPersistableException(String message) {
        super(message);
    }
}
