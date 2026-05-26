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

/**
 * Класс UDP-сервера. Отвечает за прием датаграмм,
 * десериализацию запросов, их обработку через WorkerManager и отправку сериализованных ответов.
 * Работает в однопоточном режиме.
 */
public class UDPServer {
    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);
    private final int port;
    private final WorkerManager workerManager;
    private DatagramSocket socket;
    private boolean running = false;
    private final int BUFFER_SIZE = 65535;

    public UDPServer(int port, WorkerManager workerManager) {
        this.port = port;
        this.workerManager = workerManager;
    }

    /**
     * Запускает серверный цикл прослушивания UDP-порта.
     */
    public void start() {
        try {
            socket = new DatagramSocket(port);
            running = true;
            logger.info("UDP-сервер успешно запущен на порту: " + port);

            byte[] buffer = new byte[BUFFER_SIZE];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet); // Ожидание запроса от клиента

                    logger.info("Получен новый пакет от клиента: IP=" + packet.getAddress() + ", Port=" + packet.getPort());

                    byte[] requestBytes = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), requestBytes, 0, packet.getLength());

                    // Десериализуем запрос
                    Request request;
                    try {
                        request = deserialize(requestBytes);
                        logger.info("Десериализован запрос: " + request.getCommandName() + 
                                " (аргумент: '" + request.getCommandStringArgument() + "')");
                    } catch (Exception e) {
                        logger.error("Ошибка десериализации запроса: " + e.getMessage(), e);
                        sendResponse(new Response(ResponseCode.ERROR, "Ошибка десериализации запроса на сервере!"), packet);
                        continue;
                    }

                    // Обрабатываем запрос
                    Response response = handleRequest(request);

                    // Отправляем ответ
                    sendResponse(response, packet);

                } catch (SocketException e) {
                    if (!running) {
                        logger.info("Сокет сервера успешно закрыт.");
                    } else {
                        logger.error("Ошибка сокета сервера: " + e.getMessage(), e);
                    }
                } catch (IOException e) {
                    logger.error("Ошибка при обмене данными по сети: " + e.getMessage(), e);
                }
            }

        } catch (SocketException e) {
            logger.error("Не удалось запустить сервер на порту " + port + ": " + e.getMessage(), e);
        }
    }

    /**
     * Останавливает серверный цикл и закрывает сокет.
     */
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        logger.info("UDP-сервер остановлен.");
    }

    /**
     * Обрабатывает полученный запрос от клиента и формирует ответ.
     */
    private Response handleRequest(Request request) {
        String command = request.getCommandName().toLowerCase();
        String arg = request.getCommandStringArgument();
        Serializable obj = request.getCommandObjectArgument();

        try {
            switch (command) {
                case "help":
                    return new Response(ResponseCode.OK, getHelpText());
                case "info":
                    return new Response(ResponseCode.OK, workerManager.info());
                case "show":
                    return new Response(ResponseCode.OK, workerManager.show());
                case "add":
                    if (obj instanceof Worker) {
                        return new Response(ResponseCode.OK, workerManager.add((Worker) obj));
                    }
                    return new Response(ResponseCode.ERROR, "Некорректный тип объекта для команды add.");
                case "update":
                    if (arg.isEmpty()) {
                        return new Response(ResponseCode.ERROR, "Команда update требует аргумент ID.");
                    }
                    long updateId = Long.parseLong(arg);
                    if (obj instanceof Worker) {
                        return new Response(ResponseCode.OK, workerManager.update(updateId, (Worker) obj));
                    }
                    return new Response(ResponseCode.ERROR, "Некорректный тип объекта для команды update.");
                case "remove_by_id":
                    if (arg.isEmpty()) {
                        return new Response(ResponseCode.ERROR, "Команда remove_by_id требует аргумент ID.");
                    }
                    long removeId = Long.parseLong(arg);
                    return new Response(ResponseCode.OK, workerManager.removeById(removeId));
                case "clear":
                    return new Response(ResponseCode.OK, workerManager.clear());
                case "head":
                    return new Response(ResponseCode.OK, workerManager.head());
                case "add_if_max":
                    if (obj instanceof Worker) {
                        return new Response(ResponseCode.OK, workerManager.addIfMax((Worker) obj));
                    }
                    return new Response(ResponseCode.ERROR, "Некорректный тип объекта для команды add_if_max.");
                case "add_if_min":
                    if (obj instanceof Worker) {
                        return new Response(ResponseCode.OK, workerManager.addIfMin((Worker) obj));
                    }
                    return new Response(ResponseCode.ERROR, "Некорректный тип объекта для команды add_if_min.");
                case "average_of_salary":
                    return new Response(ResponseCode.OK, workerManager.averageOfSalary());
                case "count_by_status":
                    if (arg.isEmpty()) {
                        return new Response(ResponseCode.ERROR, "Команда count_by_status требует аргумент Status.");
                    }
                    Status status = Status.valueOf(arg.toUpperCase());
                    return new Response(ResponseCode.OK, workerManager.countByStatus(status));
                case "filter_by_salary":
                    if (arg.isEmpty()) {
                        return new Response(ResponseCode.ERROR, "Команда filter_by_salary требует аргумент Salary.");
                    }
                    Integer salary = arg.equalsIgnoreCase("null") ? null : Integer.parseInt(arg);
                    return new Response(ResponseCode.OK, workerManager.filterBySalary(salary));
                default:
                    logger.warn("Получена неизвестная команда от клиента: " + command);
                    return new Response(ResponseCode.ERROR, "Неизвестная команда '" + command + "'. Введите 'help' для списка.");
            }
        } catch (NumberFormatException e) {
            logger.warn("Неверный формат числового аргумента: " + arg);
            return new Response(ResponseCode.ERROR, "Ошибка: неверный формат числового аргумента.");
        } catch (IllegalArgumentException e) {
            logger.warn("Неверное значение аргумента (enum/иное): " + arg);
            return new Response(ResponseCode.ERROR, "Ошибка: неверное значение аргумента.");
        } catch (Exception e) {
            logger.error("Ошибка при обработке запроса: " + e.getMessage(), e);
            return new Response(ResponseCode.ERROR, "Внутренняя ошибка сервера: " + e.getMessage());
        }
    }

    /**
     * Отправляет сериализованный ответ клиенту.
     */
    private void sendResponse(Response response, DatagramPacket clientPacket) throws IOException {
        byte[] responseBytes = serialize(response);
        if (responseBytes.length > BUFFER_SIZE) {
            logger.error("Размер ответа (" + responseBytes.length + " байт) превышает лимит UDP!");
            responseBytes = serialize(new Response(ResponseCode.ERROR, "Ошибка: размер ответа превысил допустимый лимит UDP пакета."));
        }

        DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, clientPacket.getAddress(), clientPacket.getPort());
        socket.send(responsePacket);
        logger.info("Отправлен ответ клиенту: IP=" + clientPacket.getAddress() + 
                ", Port=" + clientPacket.getPort() + " (размер: " + responseBytes.length + " байт)");
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
        return "Доступные команды:\n" +
                "  help : вывести справку по доступным командам\n" +
                "  info : вывести информацию о коллекции\n" +
                "  show : вывести все элементы коллекции в строковом представлении\n" +
                "  add {element} : добавить новый элемент в коллекцию\n" +
                "  update id {element} : обновить значение элемента коллекции, id которого равен заданному\n" +
                "  remove_by_id id : удалить элемент из коллекции по его id\n" +
                "  clear : очистить коллекцию\n" +
                "  execute_script file_name : считать и исполнить скрипт из указанного файла\n" +
                "  exit : завершить работу клиентского приложения\n" +
                "  head : вывести первый элемент коллекции\n" +
                "  add_if_max {element} : добавить новый элемент в коллекцию, если его значение превышает максимальное\n" +
                "  add_if_min {element} : добавить новый элемент в коллекцию, если его значение меньше минимального\n" +
                "  average_of_salary : вывести среднее значение поля salary для всех элементов коллекции\n" +
                "  count_by_status status : вывести количество элементов, значение поля status которых равно заданному\n" +
                "  filter_by_salary salary : вывести элементы, значение поля salary которых равно заданному";
    }
}
