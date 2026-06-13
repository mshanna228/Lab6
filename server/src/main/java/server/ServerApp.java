package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Точка входа для серверного приложения (ЛР7).
 *
 * <p>Изменения относительно ЛР6:</p>
 * <ul>
 *   <li>Аргумент файла коллекции больше не нужен (хранение в PostgreSQL).</li>
 *   <li>Вместо FileManager + WorkerManager(fileManager) создаётся WorkerManager(),
 *       который сам загружает коллекцию из БД.</li>
 *   <li>При завершении работы соединение с БД корректно закрывается.</li>
 * </ul>
 *
 * <p>Запуск: {@code java -jar server.jar} (без аргументов)</p>
 */
public class ServerApp {
    private static final Logger logger = LoggerFactory.getLogger(ServerApp.class);
    private static final int DEFAULT_PORT = 1821;

    public static void main(String[] args) {
        logger.info("Инициализация сервера ЛР7...");

        // Инициализируем DatabaseManager (синглтон):
        // подключение к PostgreSQL и создание таблиц происходит в конструкторе.
        // DatabaseManager.INSTANCE вызывается неявно через WorkerManager.
        WorkerManager workerManager = new WorkerManager();

        // Настраиваем Shutdown Hook: закрываем соединение с БД при завершении сервера
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Завершение работы сервера. Закрытие соединения с БД...");
            DatabaseManager.INSTANCE.closeConnection();
            logger.info("Соединение с БД закрыто.");
        }));

        // Создаём UDP-сервер с многопоточностью
        UDPServer udpServer = new UDPServer(DEFAULT_PORT, workerManager);

        // Поток для чтения консольных команд администратора сервера (exit)
        Thread consoleThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                logger.info("Консоль сервера запущена. Доступна команда: 'exit'.");
                while (true) {
                    String line = reader.readLine();
                    if (line == null) break;
                    line = line.trim().toLowerCase();
                    if (line.isEmpty()) continue;

                    if (line.equals("exit")) {
                        logger.info("Получена команда 'exit'. Сервер завершает работу...");
                        udpServer.stop();
                        System.exit(0);
                    } else {
                        System.out.println("Неизвестная команда: '" + line + "'. Доступна: exit");
                    }
                }
            } catch (Exception e) {
                logger.error("Ошибка консоли сервера: " + e.getMessage(), e);
            }
        });
        consoleThread.setDaemon(true);
        consoleThread.start();

        // Запуск UDP-сервера (блокирующий вызов)
        logger.info("Запуск UDP-сервера на порту " + DEFAULT_PORT);
        udpServer.start();
    }
}
