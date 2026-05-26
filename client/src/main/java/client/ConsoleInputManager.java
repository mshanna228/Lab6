package client;

import java.util.Scanner;
import java.util.NoSuchElementException;

/**
 * Оболочка над Scanner для работы с консольным вводом на клиенте.
 */
public class ConsoleInputManager {
    private final Scanner scanner;

    public ConsoleInputManager(Scanner scanner) {
        this.scanner = scanner;
    }

    /**
     * Читает строку из консоли.
     * @param message Приглашение к вводу.
     * @return Введенная строка или null.
     */
    public String readLine(String message) {
        System.out.print(message + " ");
        try {
            if (scanner.hasNextLine()) {
                return scanner.nextLine().trim();
            }
        } catch (NoSuchElementException e) {
            return null;
        }
        return null;
    }

    /**
     * Проверяет, есть ли следующая строка.
     * @return true, если есть.
     */
    public boolean hasNextLine() {
        return scanner.hasNextLine();
    }
}
