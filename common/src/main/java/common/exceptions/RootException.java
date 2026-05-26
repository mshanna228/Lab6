package common.exceptions;

/**
 * Базовое исключение для всего проекта.
 */
public class RootException extends RuntimeException {
    public RootException(String message) {
        super(message);
    }
}
