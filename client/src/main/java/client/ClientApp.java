package client;

import common.interaction.Request;
import common.interaction.Response;
import common.interaction.ResponseCode;
import common.model.Worker;

import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * Точка входа для клиентского приложения.
 * Управляет интерактивным вводом, обработкой скриптов и отправкой запросов на сервер.
 */
public class ClientApp {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 1821;

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат порта. Используется порт по умолчанию: " + DEFAULT_PORT);
            }
        }

        System.out.println("Запуск клиента. Подключение к серверу " + host + ":" + port);

        UDPClient client = new UDPClient(host, port);
        try {
            client.start();
        } catch (Exception e) {
            System.err.println("Не удалось запустить сетевой клиент: " + e.getMessage());
            System.exit(1);
        }

        ConsoleInputManager consoleInput = new ConsoleInputManager(new Scanner(System.in));
        ScriptReader scriptReader = new ScriptReader();
        WorkerReader workerReader = new WorkerReader(consoleInput, scriptReader);

        System.out.println("=========================================");
        System.out.println("Клиент запущен. Введите 'help' для списка команд.");
        System.out.println("=========================================");

        while (true) {
            String line;
            if (!scriptReader.isEmpty()) {
                line = scriptReader.readLine();
                if (line == null) {
                    continue; // Скрипт закончился, переходим к следующему или консоли
                }
                System.out.println("> " + line);
            } else {
                line = consoleInput.readLine(">");
            }

            if (line == null) {
                break; // Выход (Ctrl+D)
            }

            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] tokens = line.split("\\s+", 2);
            String commandName = tokens[0].toLowerCase();
            String argument = tokens.length > 1 ? tokens[1].trim() : "";

            // Локальная обработка некоторых команд
            if (commandName.equals("exit")) {
                System.out.println("Завершение работы клиента.");
                break;
            }

            if (commandName.equals("save")) {
                System.out.println("Команда 'save' недоступна клиенту.");
                continue;
            }

            if (commandName.equals("execute_script")) {
                if (argument.isEmpty()) {
                    System.out.println("Ошибка: укажите имя файла скрипта.");
                    continue;
                }
                try {
                    scriptReader.pushFile(argument);
                    System.out.println("Начало выполнения скрипта: " + argument);
                } catch (FileNotFoundException e) {
                    System.out.println("Ошибка: файл скрипта не найден: " + argument);
                } catch (Exception e) {
                    System.out.println("Ошибка при загрузке скрипта: " + e.getMessage());
                }
                continue;
            }

            // Формирование запроса к серверу
            Request request = null;
            try {
                if (commandName.equals("add") || commandName.equals("add_if_max") || commandName.equals("add_if_min")) {
                    System.out.println("Введите данные для нового рабочего:");
                    Worker worker = workerReader.readWorker();
                    request = new Request(commandName, argument, worker);
                } else if (commandName.equals("update")) {
                    if (argument.isEmpty()) {
                        System.out.println("Ошибка: команда update требует ID в качестве аргумента.");
                        continue;
                    }
                    // Валидация ID
                    try {
                        Long.parseLong(argument);
                    } catch (NumberFormatException e) {
                        System.out.println("Ошибка: ID должен быть числом.");
                        continue;
                    }
                    System.out.println("Введите новые данные для рабочего:");
                    Worker worker = workerReader.readWorker();
                    request = new Request(commandName, argument, worker);
                } else {
                    // Обычная команда без сложных объектов
                    request = new Request(commandName, argument);
                }
            } catch (Exception e) {
                System.out.println("Ошибка при формировании команды: " + e.getMessage());
                continue;
            }

            // Отправка запроса на сервер
            Response response = client.sendAndReceive(request);

            // Обработка ответа
            if (response.getResponseCode() == ResponseCode.OK) {
                System.out.println(response.getResponseBody());
            } else {
                System.err.println("Ошибка: " + response.getResponseBody());
            }
        }

        client.stop();
    }
}
