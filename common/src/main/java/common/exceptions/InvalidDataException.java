package common.exceptions;

/**
 * Исключение, выбрасываемое при вводе некорректных данных.
 */
public class InvalidDataException extends RootException {
    public InvalidDataException(String message) {
        super(message);
    }
}
