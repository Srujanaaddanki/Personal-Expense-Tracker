package com.expense.exception;

/**
 * Unchecked exception thrown when an error occurs during expense persistence or retrieval.
 * 
 * <p>Extending {@link RuntimeException} allows the service layer to signal I/O and data format
 * failures while preserving backward compatibility with existing interfaces.
 */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
