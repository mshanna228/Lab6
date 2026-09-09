package server;

import common.interaction.Request;
import common.interaction.Response;
import common.interaction.ResponseCode;
import common.model.Status;
import common.model.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

/**
 * UDP-сервер с многопоточной обработкой запросов (требование ЛР7).
 *
 * <p>Архитектура многопоточности:</p>
 * <ul>
 *   <li><b>FixedThreadPool (readPool)</b> — чтение байтов из сокета и десериализация Request.</li>
 *   <li><b>ForkJoinPool (forkJoinPool_1)</b> — проверка авторизации и выполнение команд.</li>
 *   <li><b>ForkJoinPool (forkJoinPool_2)</b> — сериализация Response и отправка клиенту.</li>
 * </ul>
 */
public class UDPServer {
    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);

    private final int port;
    private final WorkerManager workerManager;
    private DatagramSocket socket;
    private volatile boolean running = false;

    private static final int BUFFER_SIZE = 65535;

    // 1. FixedThreadPool для чтения запросов (ТЗ)
    private final ExecutorService readPool = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors())
    );

    // 2. ForkJoinPool для обработки полученного запроса (ТЗ)
    private final ForkJoinPool forkJoinPool_1 = new ForkJoinPool(
            Runtime.getRuntime().availableProcessors()
    );

    // 3. ForkJoinPool для отправки ответа (ТЗ)
    private final ForkJoinPool forkJoinPool_2 = new ForkJoinPool(
            Runtime.getRuntime().availableProcessors() * 2
    );

    public UDPServer(int port, WorkerManager workerManager) {
        this.port = port;
        this.workerManager = workerManager;
    }

    public void start() {
        try {
            socket = new DatagramSocket(port);
            running = true;
            logger.info("UDP-сервер запущен на порту: {} | FixedThreadPool: {} потоков | ForkJoinPool_1: {} потоков | ForkJoinPool_2: {} потоков",
                    port,
                    Math.max(4, Runtime.getRuntime().availableProcessors()),
                    forkJoinPool_1.getParallelism(),
                    forkJoinPool_2.getParallelism());

            byte[] buffer = new byte[BUFFER_SIZE];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                    final java.net.InetAddress clientAddr = packet.getAddress();
                    final int clientPort = packet.getPort();

                    logger.info("Получен пакет от: {}:{}", clientAddr, clientPort);

                    // Передача в readPool (FixedThreadPool) для чтения и десериализации
                    readPool.submit(() -> processPacket(data, clientAddr, clientPort));

                } catch (SocketException e) {
                    if (!running) {
                        logger.info("Сокет сервера закрыт — завершение цикла.");
                    } else {
                        logger.error("Ошибка сокета: {}", e.getMessage(), e);
                    }
                } catch (IOException e) {
                    logger.error("Ошибка приёма пакета: {}", e.getMessage(), e);
                }
            }
        } catch (SocketException e) {
            logger.error("Не удалось запустить сервер на порту {}: {}", port, e.getMessage(), e);
        }
    }

    private void processPacket(byte[] data, java.net.InetAddress clientAddr, int clientPort) {
        Request request;
        try {
            request = deserialize(data);
            logger.info("Десериализован запрос: {} | login={}", request.getCommandName(), request.getLogin());
        } catch (Exception e) {
            logger.error("Ошибка десериализации: {}", e.getMessage(), e);
            sendResponseAsync(new Response(ResponseCode.ERROR, "Ошибка десериализации запроса."), clientAddr, clientPort);
            return;
        }

        final Request finalRequest = request;

        // Передача задачи обработки в forkJoinPool_1
        forkJoinPool_1.submit(() -> {
            Response response = handleRequest(finalRequest);
            // Передача сформированного ответа на отправку в forkJoinPool_2
            sendResponseAsync(response, clientAddr, clientPort);
        });
    }

    private void sendResponseAsync(Response response, java.net.InetAddress addr, int port) {
        // Передача задачи отправки в forkJoinPool_2
        forkJoinPool_2.submit(() -> {
            try {
                sendResponse(response, addr, port);
            } catch (IOException e) {
                logger.error("Ошибка отправки ответа: {}", e.getMessage(), e);
            }
        });
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        readPool.shutdown();
        forkJoinPool_1.shutdown();
        forkJoinPool_2.shutdown();
        logger.info("UDP-сервер остановлен.");
    }

    private Response handleRequest(Request request) {
        String command = request.getCommandName().toLowerCase();
        String arg = request.getCommandStringArgument();
        Serializable obj = request.getCommandObjectArgument();
        String login = request.getLogin();
        String password = request.getPassword();

        // Команды без авторизации
        if (command.equals("register")) {
            return handleRegister(arg);
        }
        if (command.equals("login")) {
            return handleLogin(login, password);
        }

        if (login.isBlank() || password.isBlank()) {
            return new Response(ResponseCode.ERROR, "Необходима авторизация. Используйте команду 'login'.");
        }
        if (!DatabaseManager.INSTANCE.authenticateUser(login, password)) {
            return new Response(ResponseCode.ERROR, "Неверный логин или пароль.");
        }

        try {
            return switch (command) {
                case "help" -> new Response(ResponseCode.OK, getHelpText());
                case "info" -> new Response(ResponseCode.OK, workerManager.info());
                case "show" -> new Response(ResponseCode.OK, workerManager.show());
                case "head" -> new Response(ResponseCode.OK, workerManager.head());
                case "average_of_salary" -> new Response(ResponseCode.OK, workerManager.averageOfSalary());

                case "add" -> {
                    if (!(obj instanceof Worker w)) {
                        yield new Response(ResponseCode.ERROR, "Некорректный тип объекта для команды add.");
                    }
                    yield new Response(ResponseCode.OK, workerManager.add(w, login));
                }

                case "update" -> {
                    if (arg.isEmpty()) {
                        yield new Response(ResponseCode.ERROR, "Команда update требует аргумент ID.");
                    }
                    long updateId = Long.parseLong(arg);
                    if (!(obj instanceof Worker w)) {
                        yield new Response(ResponseCode.ERROR, "Некорректный тип объекта для команды update.");
                    }
                    yield new Response(ResponseCode.OK, workerManager.update(updateId, w, login));
                }

                case "remove_by_id" -> {
                    if (arg.isEmpty()) {
                        yield new Response(ResponseCode.ERROR, "Команда remove_by_id требует аргумент ID.");
                    }
                    long removeId = Long.parseLong(arg);
                    yield new Response(ResponseCode.OK, workerManager.removeById(removeId, login));
                }

                case "clear" -> new Response(ResponseCode.OK, workerManager.clear(login));

                case "add_if_max" -> {
                    if (!(obj instanceof Worker w)) {
                        yield new Response(ResponseCode.ERROR, "Некорректный тип объекта для команды add_if_max.");
                    }
                    yield new Response(ResponseCode.OK, workerManager.addIfMax(w, login));
                }

                case "add_if_min" -> {
                    if (!(obj instanceof Worker w)) {
                        yield new Response(ResponseCode.ERROR, "Некорректный тип объекта для команды add_if_min.");
                    }
                    yield new Response(ResponseCode.OK, workerManager.addIfMin(w, login));
                }

                case "count_by_status" -> {
                    if (arg.isEmpty()) {
                        yield new Response(ResponseCode.ERROR, "Команда count_by_status требует аргумент Status.");
                    }
                    Status status = Status.valueOf(arg.toUpperCase());
                    yield new Response(ResponseCode.OK, workerManager.countByStatus(status));
                }

                case "filter_by_salary" -> {
                    if (arg.isEmpty()) {
                        yield new Response(ResponseCode.ERROR, "Команда filter_by_salary требует аргумент Salary.");
                    }
                    Integer salary = arg.equalsIgnoreCase("null") ? null : Integer.parseInt(arg);
                    yield new Response(ResponseCode.OK, workerManager.filterBySalary(salary));
                }

                default -> {
                    logger.warn("Неизвестная команда: {}", command);
                    yield new Response(ResponseCode.ERROR, "Неизвестная команда '" + command + "'. Введите 'help'.");
                }
            };
        } catch (NumberFormatException e) {
            logger.warn("Неверный формат числового аргумента: {}", arg);
            return new Response(ResponseCode.ERROR, "Ошибка: неверный формат числового аргумента.");
        } catch (IllegalArgumentException e) {
            logger.warn("Неверное значение аргумента: {}", arg);
            return new Response(ResponseCode.ERROR, "Ошибка: неверное значение аргумента.");
        } catch (Exception e) {
            logger.error("Ошибка обработки запроса: {}", e.getMessage(), e);
            return new Response(ResponseCode.ERROR, "Внутренняя ошибка сервера: " + e.getMessage());
        }
    }

    private Response handleRegister(String arg) {
        if (arg == null || !arg.contains(" ")) {
            return new Response(ResponseCode.ERROR, "Использование: register <логин> <пароль>");
        }
        String[] parts = arg.split("\\s+", 2);
        if (parts.length < 2) {
            return new Response(ResponseCode.ERROR, "Использование: register <логин> <пароль>");
        }
        String username = parts[0].trim();
        String password = parts[1].trim();

        if (DatabaseManager.INSTANCE.registerUser(username, password)) {
            logger.info("Зарегистрирован пользователь: {}", username);
            return new Response(ResponseCode.OK, "Регистрация успешна. Добро пожаловать, " + username + "!");
        } else {
            return new Response(ResponseCode.ERROR, "Логин '" + username + "' уже занят. Выберите другой.");
        }
    }

    private Response handleLogin(String login, String password) {
        if (login.isBlank() || password.isBlank()) {
            return new Response(ResponseCode.ERROR, "Использование: login <логин> <пароль>");
        }
        if (DatabaseManager.INSTANCE.authenticateUser(login, password)) {
            logger.info("Успешный вход: {}", login);
            return new Response(ResponseCode.OK, "Авторизация успешна. Добро пожаловать, " + login + "!");
        } else {
            return new Response(ResponseCode.ERROR, "Неверный логин или пароль.");
        }
    }

    private void sendResponse(Response response, java.net.InetAddress addr, int port) throws IOException {
        byte[] responseBytes = serialize(response);
        if (responseBytes.length > BUFFER_SIZE) {
            logger.error("Ответ превышает лимит UDP: {} байт", responseBytes.length);
            responseBytes = serialize(new Response(ResponseCode.ERROR, "Ошибка: ответ превысил лимит UDP."));
        }
        DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, addr, port);
        socket.send(responsePacket);
        logger.info("Отправлен ответ: {}:{} ({} байт)", addr, port, responseBytes.length);
    }

    private byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        }
    }

    private Request deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (Request) ois.readObject();
        }
    }

    private String getHelpText() {
        return """
                Доступные команды:
                  register <логин> <пароль>     — зарегистрировать нового пользователя
                  login <логин> <пароль>        — войти в систему (сохраняет сессию на клиенте)
                  help                          — вывести справку
                  info                          — информация о коллекции
                  show                          — показать все элементы коллекции
                  add {element}                 — добавить новый элемент
                  update <id> {element}         — обновить элемент по ID (только свой)
                  remove_by_id <id>             — удалить элемент по ID (только свой)
                  clear                         — удалить все свои элементы из коллекции
                  head                          — показать первый элемент коллекции
                  add_if_max {element}          — добавить, если значение максимальное
                  add_if_min {element}          — добавить, если значение минимальное
                  average_of_salary             — средняя зарплата в коллекции
                  count_by_status <status>      — количество элементов с заданным статусом
                  filter_by_salary <salary>     — элементы с заданной зарплатой
                  execute_script <file>         — выполнить скрипт из файла
                  exit                          — завершить работу клиента""";
    }
}