package com.ecs160.persistence;

/*
 * Exception thrown if there is no field labelled as @PersistableId in a @Persistable class
 */
public class IdException extends PersistenceException {
    /**
     * Construct new instance of IdException
     * @param message message for why IdException is thrown
     */
    public IdException(String message) {
        super(message);
    }
}
