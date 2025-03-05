package com.ecs160.Parser;

/*
 * Exception that is thrown when the Parser cannot continue parsing a given JSON file.
 */
public class ParserException extends Exception {
    /**
     * ParserException is constructed using a String message
     * @param excMsg message for why parsing cannot continue
     */
    public ParserException(String excMsg) {
        super(excMsg);
    }
}
