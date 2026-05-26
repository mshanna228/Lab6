package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Точка входа для серверного приложения.
 * Инициализирует менеджеры, запускает UDP-сервер, обрабатывает консольные команды сервера
 * и настраивает автоматическое сохранение при выходе.
 */
public class ServerApp {
    private static final Logger logger = LoggerFactory.getLogger(ServerApp.class);
    private static final int DEFAULT_PORT = 1821;

    public static void main(String[] args) {
        if (args.length == 0) {
            logger.error("Ошибка: Имя файла коллекции JSON должно передаваться как аргумент командной строки.");
            System.err.println("Использование: java -jar server.jar <имя_файла_коллекции>");
            System.exit(1);
        }

        String fileName = args[0];
        logger.info("Инициализация сервера. Файл коллекции: " + fileName);

        // Инициализируем FileManager и WorkerManager
        FileManager fileManager = new FileManager(fileName);
        WorkerManager workerManager = new WorkerManager(fileManager);

        // Настраиваем Shutdown Hook для автосохранения коллекции при аварийном или обычном завершении
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Получен сигнал завершения работы JVM. Сохранение коллекции перед выходом...");
            workerManager.save();
            logger.info("Автоматическое сохранение успешно выполнено.");
        }));

        // Создаем UDP-сервер
        UDPServer udpServer = new UDPServer(DEFAULT_PORT, workerManager);

        // Поток для чтения консольных команд администратора сервера (save, exit)
        Thread consoleThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                logger.info("Поток консоли сервера запущен. Доступны команды: 'save', 'exit'.");
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    line = line.trim().toLowerCase();
                    if (line.isEmpty()) {
                        continue;
                    }

                    if (line.equals("save")) {
                        logger.info("Получена команда сохранения коллекции от администратора сервера.");
                        workerManager.save();
                        System.out.println("Коллекция успешно сохранена в файл.");
                    } else if (line.equals("exit")) {
                        logger.info("Получена команда завершения работы от администратора сервера.");
                        workerManager.save();
                        System.out.println("Коллекция успешно сохранена. Сервер завершает работу...");
                        udpServer.stop();
                        System.exit(0);
                    } else {
                        System.out.println("Неизвестная консольная команда сервера: '" + line + "'. Доступны: save, exit");
                    }
                }
            } catch (Exception e) {
                logger.error("Ошибка в потоке чтения консоли сервера: " + e.getMessage(), e);
            }
        });
        consoleThread.setDaemon(true);
        consoleThread.start();

        // Запуск UDP-сервера (блокирующий вызов в главном потоке)
        logger.info("Запуск UDP-сервера на порту " + DEFAULT_PORT);
        udpServer.start();
    }
}
