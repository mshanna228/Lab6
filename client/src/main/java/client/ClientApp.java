package client;

import common.interaction.Request;
import common.interaction.Response;
import common.interaction.ResponseCode;
import common.model.Worker;

import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * Точка входа для клиентского приложения (ЛР7).
 *
 * <p>Изменения относительно ЛР6:</p>
 * <ul>
 *   <li>При старте клиент предлагает зарегистрироваться или войти.</li>
 *   <li>После авторизации логин и пароль сохраняются в памяти и
 *       автоматически добавляются в каждый запрос к серверу.</li>
 *   <li>Добавлена команда register (для новых пользователей).</li>
 * </ul>
 *
 * <p>Запуск: {@code java -jar client.jar [host] [port]}</p>
 */
public class ClientApp {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 1821;

    /** Текущий логин авторизованного пользователя. */
    private static String currentLogin = "";
    /** Текущий пароль (хранится в памяти клиента). */
    private static String currentPassword = "";

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length > 0) host = args[0];
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Неверный формат порта. Используется: " + DEFAULT_PORT);
            }
        }

        System.out.println("Запуск клиента. Подключение к серверу " + host + ":" + port);

        UDPClient client = new UDPClient(host, port);
        try {
            client.start();
        } catch (Exception e) {
            System.err.println("Не удалось подключиться: " + e.getMessage());
            System.exit(1);
        }

        Scanner scanner = new Scanner(System.in);
        ConsoleInputManager consoleInput = new ConsoleInputManager(scanner);
        ScriptReader scriptReader = new ScriptReader();
        WorkerReader workerReader = new WorkerReader(consoleInput, scriptReader);

        //  Авторизация/регистрация при запуске
        System.out.println("◸—————————————————————————————————————◹");
        System.out.println("  Менеджер коллекции рабочих (ЛР7)");
        System.out.println("◸—————————————————————————————————————◹");
        System.out.println("Вы не авторизованы. Выберите действие:");
        System.out.println("  register <логин> <пароль>  — регистрация");
        System.out.println("  login <логин> <пароль>     — вход");
        System.out.println("◸—————————————————————————————————————◹");

        //  авторизации в начале
        while (currentLogin.isEmpty()) {
            System.out.print("> ");
            String line = scanner.hasNextLine() ? scanner.nextLine().trim() : null;
            if (line == null || line.equals("exit")) {
                System.out.println("Выход из клиента.");
                client.stop();
                return;
            }
            if (line.isEmpty()) continue;

            String[] tokens = line.split("\\s+", 3);
            String cmd = tokens[0].toLowerCase();

            if (cmd.equals("register")) {
                if (tokens.length < 3) {
                    System.out.println("Использование: register <логин> <пароль>");
                    continue;
                }
                //  отправляем регистрацию на сервер (аргумент: "login password")
                Request req = new Request("register", tokens[1] + " " + tokens[2]);
                Response resp = client.sendAndReceive(req);
                System.out.println(resp.getResponseBody());
                if (resp.getResponseCode() == ResponseCode.OK) {
                    currentLogin = tokens[1];
                    currentPassword = tokens[2];
                }

            } else if (cmd.equals("login")) {
                if (tokens.length < 3) {
                    System.out.println("Использование: login <логин> <пароль>");
                    continue;
                }
                // Проверяем логин/пароль на сервере
                Request req = buildRequest("login", "", null, tokens[1], tokens[2]);
                Response resp = client.sendAndReceive(req);
                System.out.println(resp.getResponseBody());
                if (resp.getResponseCode() == ResponseCode.OK) {
                    currentLogin = tokens[1];
                    currentPassword = tokens[2];
                }

            } else {
                System.out.println("Сначала войдите: login <логин> <пароль>  или  register <логин> <пароль>");
            }
        }

        System.out.println("\nДобро пожаловать, " + currentLogin + "! Введите 'help' для списка команд.");
        System.out.println("=========================================\n");

        // --- Основной цикл команд ---
        while (true) {
            String line;
            if (!scriptReader.isEmpty()) {
                line = scriptReader.readLine();
                if (line == null) continue;
                System.out.println("> " + line);
            } else {
                line = consoleInput.readLine(currentLogin + "@worker> ");
            }

            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] tokens = line.split("\\s+", 2);
            String commandName = tokens[0].toLowerCase();
            String argument = tokens.length > 1 ? tokens[1].trim() : "";

            // --- Локальные команды ---
            if (commandName.equals("exit")) {
                System.out.println("Завершение работы клиента.");
                break;
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
                    System.out.println("Файл скрипта не найден: " + argument);
                } catch (Exception e) {
                    System.out.println("Ошибка загрузки скрипта: " + e.getMessage());
                }
                continue;
            }

            // Команда register/login в основном цикле — позволяем сменить пользователя
            if (commandName.equals("register") || commandName.equals("login")) {
                String[] subTokens = line.split("\\s+", 3);
                if (subTokens.length < 3) {
                    System.out.println("Использование: " + commandName + " <логин> <пароль>");
                    continue;
                }
                String newLogin = subTokens[1];
                String newPass = subTokens[2];
                Request req;
                if (commandName.equals("register")) {
                    req = new Request("register", newLogin + " " + newPass);
                } else {
                    req = buildRequest("login", "", null, newLogin, newPass);
                }
                Response resp = client.sendAndReceive(req);
                System.out.println(resp.getResponseBody());
                if (resp.getResponseCode() == ResponseCode.OK) {
                    currentLogin = newLogin;
                    currentPassword = newPass;
                    System.out.println("Текущий пользователь: " + currentLogin);
                }
                continue;
            }

            // --- Формирование запроса к серверу ---
            Request request = null;
            try {
                if (commandName.equals("add") || commandName.equals("add_if_max") || commandName.equals("add_if_min")) {
                    System.out.println("Введите данные нового рабочего:");
                    Worker worker = workerReader.readWorker();
                    request = buildRequest(commandName, argument, worker, currentLogin, currentPassword);
                } else if (commandName.equals("update")) {
                    if (argument.isEmpty()) {
                        System.out.println("Ошибка: укажите ID. Пример: update 5");
                        continue;
                    }
                    try {
                        Long.parseLong(argument);
                    } catch (NumberFormatException e) {
                        System.out.println("Ошибка: ID должен быть числом.");
                        continue;
                    }
                    System.out.println("Введите новые данные рабочего:");
                    Worker worker = workerReader.readWorker();
                    request = buildRequest(commandName, argument, worker, currentLogin, currentPassword);
                } else {
                    request = buildRequest(commandName, argument, null, currentLogin, currentPassword);
                }
            } catch (Exception e) {
                System.out.println("Ошибка при формировании команды: " + e.getMessage());
                continue;
            }

            // --- Отправка на сервер и обработка ответа ---
            Response response = client.sendAndReceive(request);
            if (response.getResponseCode() == ResponseCode.OK) {
                System.out.println(response.getResponseBody());
            } else {
                System.err.println("Ошибка: " + response.getResponseBody());
            }
        }

        client.stop();
    }

    /**
     * Создаёт Request с добавлением логина и пароля текущего пользователя.
     *
     * @param commandName имя команды
     * @param arg         строковый аргумент команды
     * @param obj         объектный аргумент (Worker или null)
     * @param login       логин пользователя
     * @param password    пароль пользователя
     * @return Request с заполненными полями авторизации
     */
    private static Request buildRequest(String commandName, String arg,
                                        java.io.Serializable obj, String login, String password) {
        Request req = new Request(commandName, arg, obj);
        req.setLogin(login);
        req.setPassword(password);
        return req;
    }
}
