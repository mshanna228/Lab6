package server;

import common.interaction.Request;
import common.interaction.Response;
import common.interaction.ResponseCode;
import common.model.Worker;
import common.model.Status;
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
 *   <li><b>FixedThreadPool</b> — для чтения входящих UDP-датаграмм.
 *       Каждый поток из пула читает один пакет и делегирует его обработку.</li>
 *   <li><b>ForkJoinPool</b> — для обработки десериализованного запроса
 *       (выполнение команды через WorkerManager).</li>
 *   <li><b>ForkJoinPool</b> — для сериализации и отправки ответа клиенту.</li>
 * </ul>
 *
 * <p>Авторизация: каждый запрос содержит login/password.
 * Перед обработкой любой команды (кроме register/login) сервер проверяет
 * подлинность пользователя через DatabaseManager.</p>
 */
public class UDPServer {
    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);

    private final int port;
    private final WorkerManager workerManager;
    private DatagramSocket socket;
    private volatile boolean running = false;

    /** Размер буфера UDP-пакета (максимальный размер датаграммы). */
    private static final int BUFFER_SIZE = 65535;

    /**
     * FixedThreadPool для чтения запросов.
     * Количество потоков = количество доступных ядер процессора (или минимум 4).
     */
    private final ExecutorService readPool = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors())
    );

    /**
     * ForkJoinPool для обработки запросов и отправки ответов.
     * Используется общий пул (commonPool) — подходит для CPU-bound задач.
     */
    private final ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();

    public UDPServer(int port, WorkerManager workerManager) {
        this.port = port;
        this.workerManager = workerManager;
    }

    /**
     * Запускает серверный цикл прослушивания UDP-порта.
     * Для каждого входящего пакета запускает задачу в FixedThreadPool.
     */
    public void start() {
        try {
            socket = new DatagramSocket(port);
            running = true;
            logger.info("UDP-сервер запущен на порту: " + port +
                    " | FixedThreadPool: " + Math.max(4, Runtime.getRuntime().availableProcessors()) + " потоков" +
                    " | ForkJoinPool: " + forkJoinPool.getParallelism() + " потоков");

            byte[] buffer = new byte[BUFFER_SIZE];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    // Главный поток ждёт пакет и сразу передаёт в ReadPool
                    socket.receive(packet);

                    // Копируем данные из буфера до следующей итерации
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                    // Адрес и порт клиента для ответа
                    final java.net.InetAddress clientAddr = packet.getAddress();
                    final int clientPort = packet.getPort();

                    logger.info("Получен пакет от: " + clientAddr + ":" + clientPort);

                    // Передаём обработку в FixedThreadPool (чтение / десериализация)
                    readPool.submit(() -> processPacket(data, clientAddr, clientPort));

                } catch (SocketException e) {
                    if (!running) {
                        logger.info("Сокет сервера закрыт — завершение цикла.");
                    } else {
                        logger.error("Ошибка сокета: " + e.getMessage(), e);
                    }
                } catch (IOException e) {
                    logger.error("Ошибка приёма пакета: " + e.getMessage(), e);
                }
            }
        } catch (SocketException e) {
            logger.error("Не удалось запустить сервер на порту " + port + ": " + e.getMessage(), e);
        }
    }

    /**
     * Обрабатывает один UDP-пакет: десериализует запрос, обрабатывает через ForkJoinPool,
     * отправляет ответ через ForkJoinPool.
     * Вызывается из потока FixedThreadPool.
     *
     * @param data       байты пакета
     * @param clientAddr адрес клиента
     * @param clientPort порт клиента
     */
    private void processPacket(byte[] data, java.net.InetAddress clientAddr, int clientPort) {
        // Десериализация запроса (в потоке ReadPool)
        Request request;
        try {
            request = deserialize(data);
            logger.info("Десериализован запрос: " + request.getCommandName() +
                    " | login=" + request.getLogin());
        } catch (Exception e) {
            logger.error("Ошибка десериализации: " + e.getMessage(), e);
            sendResponseAsync(new Response(ResponseCode.ERROR, "Ошибка десериализации запроса."), clientAddr, clientPort);
            return;
        }

        // Обработка запроса в ForkJoinPool
        final Request finalRequest = request;
        forkJoinPool.submit(() -> {
            Response response = handleRequest(finalRequest);
            // Отправка ответа тоже в ForkJoinPool
            sendResponseAsync(response, clientAddr, clientPort);
        });
    }

    /**
     * Отправляет ответ клиенту асинхронно через ForkJoinPool.
     */
    private void sendResponseAsync(Response response, java.net.InetAddress addr, int port) {
        forkJoinPool.submit(() -> {
            try {
                sendResponse(response, addr, port);
            } catch (IOException e) {
                logger.error("Ошибка отправки ответа: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Остановка сервера: закрывает сокет и завершает пулы потоков.
     */
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        readPool.shutdown();
        logger.info("UDP-сервер остановлен.");
    }

    // -------------------------------------------------------------------------
    // Обработчик команд
    // -------------------------------------------------------------------------

    /**
     * Обрабатывает запрос от клиента.
     *
     * <p>Алгоритм:</p>
     * <ol>
     *   <li>Если команда register или login — обрабатывает без авторизации.</li>
     *   <li>Для всех остальных команд — проверяет login/password через DatabaseManager.</li>
     *   <li>При успешной авторизации выполняет команду через WorkerManager.</li>
     * </ol>
     *
     * @param request десериализованный запрос от клиента
     * @return ответ сервера
     */
    private Response handleRequest(Request request) {
        String command = request.getCommandName().toLowerCase();
        String arg = request.getCommandStringArgument();
        Serializable obj = request.getCommandObjectArgument();
        String login = request.getLogin();
        String password = request.getPassword();

        // --- Команды без авторизации ---
        if (command.equals("register")) {
            return handleRegister(arg);
        }
        if (command.equals("login")) {
            return handleLogin(login, password);
        }

        // --- Проверка авторизации для всех остальных команд ---
        if (login.isBlank() || password.isBlank()) {
            return new Response(ResponseCode.ERROR, "Необходима авторизация. Используйте команду 'login'.");
        }
        if (!DatabaseManager.INSTANCE.authenticateUser(login, password)) {
            return new Response(ResponseCode.ERROR, "Неверный логин или пароль.");
        }

        // --- Выполнение команды ---
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
                    logger.warn("Неизвестная команда: " + command);
                    yield new Response(ResponseCode.ERROR, "Неизвестная команда '" + command + "'. Введите 'help'.");
                }
            };
        } catch (NumberFormatException e) {
            logger.warn("Неверный формат числового аргумента: " + arg);
            return new Response(ResponseCode.ERROR, "Ошибка: неверный формат числового аргумента.");
        } catch (IllegalArgumentException e) {
            logger.warn("Неверное значение аргумента: " + arg);
            return new Response(ResponseCode.ERROR, "Ошибка: неверное значение аргумента.");
        } catch (Exception e) {
            logger.error("Ошибка обработки запроса: " + e.getMessage(), e);
            return new Response(ResponseCode.ERROR, "Внутренняя ошибка сервера: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает команду регистрации.
     * Аргумент: "username password" через пробел.
     */
    private Response handleRegister(String arg) {
        if (arg == null || !arg.contains(" ")) {
            return new Response(ResponseCode.ERROR,
                    "Использование: register <логин> <пароль>");
        }
        String[] parts = arg.split("\\s+", 2);
        if (parts.length < 2) {
            return new Response(ResponseCode.ERROR,
                    "Использование: register <логин> <пароль>");
        }
        String username = parts[0].trim();
        String password = parts[1].trim();

        if (DatabaseManager.INSTANCE.registerUser(username, password)) {
            logger.info("Зарегистрирован пользователь: " + username);
            return new Response(ResponseCode.OK, "Регистрация успешна. Добро пожаловать, " + username + "!");
        } else {
            return new Response(ResponseCode.ERROR, "Логин '" + username + "' уже занят. Выберите другой.");
        }
    }

    /**
     * Обрабатывает команду авторизации (проверяет логин/пароль).
     */
    private Response handleLogin(String login, String password) {
        if (login.isBlank() || password.isBlank()) {
            return new Response(ResponseCode.ERROR, "Использование: login <логин> <пароль>");
        }
        if (DatabaseManager.INSTANCE.authenticateUser(login, password)) {
            logger.info("Успешный вход: " + login);
            return new Response(ResponseCode.OK, "Авторизация успешна. Добро пожаловать, " + login + "!");
        } else {
            return new Response(ResponseCode.ERROR, "Неверный логин или пароль.");
        }
    }

    // -------------------------------------------------------------------------
    // Сериализация / десериализация
    // -------------------------------------------------------------------------

    private void sendResponse(Response response, java.net.InetAddress addr, int port) throws IOException {
        byte[] responseBytes = serialize(response);
        if (responseBytes.length > BUFFER_SIZE) {
            logger.error("Ответ превышает лимит UDP: " + responseBytes.length + " байт");
            responseBytes = serialize(new Response(ResponseCode.ERROR, "Ошибка: ответ превысил лимит UDP."));
        }
        DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, addr, port);
        socket.send(responsePacket);
        logger.info("Отправлен ответ: " + addr + ":" + port + " (" + responseBytes.length + " байт)");
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

    // -------------------------------------------------------------------------
    // Текст справки
    // -------------------------------------------------------------------------

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
