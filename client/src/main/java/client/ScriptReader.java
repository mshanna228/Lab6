package client;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.Stack;

/**
 * Класс для чтения команд из файлов скриптов.
 * Поддерживает вложенные скрипты и проверку на рекурсию.
 */
public class ScriptReader {
    private final Stack<Scanner> scanners = new Stack<>();
    private final Stack<String> fileNames = new Stack<>();
    private final Stack<String[]> argumentsStack = new Stack<>();

    public void pushFile(String fileName) throws FileNotFoundException {
        pushFile(fileName, new String[0]);
    }

    public void pushFile(String fileName, String[] arguments) throws FileNotFoundException {
        File file = new File(fileName);
        if (fileNames.contains(file.getAbsolutePath())) {
            throw new RuntimeException("Обнаружена бесконечная рекурсия скриптов!");
        }
        scanners.push(new Scanner(file));
        fileNames.push(file.getAbsolutePath());
        argumentsStack.push(arguments);
    }

    public String readLine() {
        while (!scanners.isEmpty()) {
            Scanner s = scanners.peek();
            if (s.hasNextLine()) {
                String line = s.nextLine().trim();
                String[] currentArgs = argumentsStack.peek();

                if (currentArgs.length > 0) {
                    for (int i = currentArgs.length; i > 0; i--) {
                        line = line.replace("$" + i, currentArgs[i - 1]);
                    }
                }
                return line;
            } else {
                scanners.pop().close();
                fileNames.pop();
                argumentsStack.pop();
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return scanners.isEmpty();
    }
}
