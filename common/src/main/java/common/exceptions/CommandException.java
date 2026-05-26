package common.exceptions;

/**
 * Исключение, выбрасываемое при ошибках в командах.
 */
public class CommandException extends RootException {
    public CommandException(String message) {
        super(message);
    }
}
