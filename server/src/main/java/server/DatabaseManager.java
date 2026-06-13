package server;

import common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.PriorityQueue;

/**
 * DatabaseManager — синглтон для работы с PostgreSQL.
 *
 * <p>Отвечает за:</p>
 * <ul>
 *   <li>Инициализацию соединения с базой данных</li>
 *   <li>Создание таблиц users, organizations, workers при старте</li>
 *   <li>Загрузку коллекции из БД в память при инициализации</li>
 *   <li>Добавление, обновление, удаление, очистку Worker-ов в БД</li>
 *   <li>Регистрацию и авторизацию пользователей (пароли хранятся как SHA-1)</li>
 * </ul>
 *
 * <p>Используется JDBC (драйвер org.postgresql) без ORM.</p>
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    /** Единственный экземпляр синглтона */
    public static final DatabaseManager INSTANCE = new DatabaseManager();

    private Connection connection;

    // -------------------------------------------------------------------------
    // Конструктор: подключение + создание таблиц
    // -------------------------------------------------------------------------

    private DatabaseManager() {
        // ---------------------------------------------------------------
        // Параметры подключения читаются из переменных среды.
        // Если переменная не задана — используется значение по умолчанию.
        //
        // Для запуска ЛОКАЛЬНО (PostgreSQL на вашем компьютере):
        //   Windows PowerShell:
        //     $env:DB_HOST="localhost"; $env:DB_PORT="5432"; $env:DB_NAME="studs"
        //     $env:DB_USER="s505045"; $env:DB_PASSWORD="ваш_пароль"
        //     .\gradlew.bat :server:run
        //
        // На КАФЕДРАЛЬНОМ СЕРВЕРЕ (хост pg доступен по сети):
        //   export DB_HOST=pg
        //   java -jar server.jar
        // ---------------------------------------------------------------
        String host     = getEnvOrDefault("DB_HOST",     "pg");
        String port     = getEnvOrDefault("DB_PORT",     "5432");
        String dbName   = getEnvOrDefault("DB_NAME",     "studs");
        String user     = getEnvOrDefault("DB_USER",     "s505045");
        String password = getEnvOrDefault("DB_PASSWORD", "x0E4qloofOgALNWu");

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
        logger.info("Подключение к БД: " + url + " (пользователь: " + user + ")");

        try {
            Class.forName("org.postgresql.Driver");
            try {
                connection = DriverManager.getConnection(url, user, password);
                logger.info("Подключение к PostgreSQL успешно установлено.");
            } catch (SQLException e) {
                if (!"localhost".equalsIgnoreCase(host)) {
                    logger.warn("Не удалось подключиться к хосту " + host + " (" + e.getMessage() + "). Пробуем подключиться к localhost...");
                    String fallbackUrl = "jdbc:postgresql://localhost:" + port + "/" + dbName;
                    connection = DriverManager.getConnection(fallbackUrl, user, password);
                    logger.info("Успешное подключение к запасному хосту (localhost).");
                } else {
                    throw e;
                }
            }
        } catch (ClassNotFoundException e) {
            logger.error("Драйвер PostgreSQL не найден! Добавьте зависимость в build.gradle.", e);
            throw new RuntimeException("PostgreSQL драйвер не найден", e);
        } catch (SQLException e) {
            logger.error("Ошибка подключения к PostgreSQL (" + url + "): " + e.getMessage(), e);
            logger.error("Подсказка: задайте переменные среды DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD");
            throw new RuntimeException("Ошибка подключения к БД", e);
        }

        createTablesIfNotExist();
    }

    /**
     * Читает переменную среды. Если не задана — возвращает defaultValue.
     */
    private static String getEnvOrDefault(String name, String defaultValue) {
        String val = System.getenv(name);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }

    /**
     * Создаёт таблицы в БД, если они ещё не существуют.
     * Порядок важен: сначала users, потом organizations, потом workers
     * (workers ссылается на обе предыдущие таблицы).
     */
    private void createTablesIfNotExist() {
        String createUsers = """
                CREATE TABLE IF NOT EXISTS s505045_users (
                    id SERIAL PRIMARY KEY,
                    username VARCHAR(100) UNIQUE NOT NULL,
                    password_sha1 CHAR(40) NOT NULL
                )
                """;

        String createOrganizations = """
                CREATE TABLE IF NOT EXISTS s505045_organizations (
                    id SERIAL PRIMARY KEY,
                    employees_count INTEGER,
                    org_type VARCHAR(50) NOT NULL
                )
                """;

        String createWorkers = """
                CREATE TABLE IF NOT EXISTS s505045_workers (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    coordinates_x INTEGER NOT NULL,
                    coordinates_y DOUBLE PRECISION NOT NULL,
                    creation_date TIMESTAMP NOT NULL,
                    salary INTEGER,
                    position VARCHAR(50) NOT NULL,
                    status VARCHAR(50),
                    organization_id INTEGER REFERENCES s505045_organizations(id) ON DELETE SET NULL,
                    owner VARCHAR(100) NOT NULL REFERENCES s505045_users(username) ON DELETE CASCADE
                )
                """;

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createUsers);
            logger.info("Таблица s505045_users готова.");
            stmt.executeUpdate(createOrganizations);
            logger.info("Таблица s505045_organizations готова.");
            stmt.executeUpdate(createWorkers);
            logger.info("Таблица s505045_workers готова.");
        } catch (SQLException e) {
            logger.error("Ошибка при создании таблиц: " + e.getMessage(), e);
            throw new RuntimeException("Ошибка инициализации БД", e);
        }
    }

    // -------------------------------------------------------------------------
    // Загрузка коллекции из БД
    // -------------------------------------------------------------------------

    /**
     * Читает все Worker-ы из БД и возвращает в виде PriorityQueue.
     * Вызывается один раз при старте сервера.
     *
     * @return коллекция всех Worker-ов из БД
     */
    public PriorityQueue<Worker> loadAllWorkers() {
        PriorityQueue<Worker> collection = new PriorityQueue<>();

        String sql = """
                SELECT w.id, w.name, w.coordinates_x, w.coordinates_y, w.creation_date,
                       w.salary, w.position, w.status,
                       o.id AS org_id, o.employees_count, o.org_type,
                       w.owner
                FROM s505045_workers w
                LEFT JOIN s505045_organizations o ON w.organization_id = o.id
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Worker worker = mapResultSetToWorker(rs);
                collection.add(worker);
            }
            logger.info("Загружено из БД рабочих: " + collection.size());
        } catch (SQLException e) {
            logger.error("Ошибка загрузки коллекции из БД: " + e.getMessage(), e);
        }
        return collection;
    }

    // -------------------------------------------------------------------------
    // CRUD операции для Worker
    // -------------------------------------------------------------------------

    /**
     * Вставляет нового Worker-а в БД.
     * Сначала вставляет Organization (если она есть), затем Worker.
     * ID генерируется SERIAL (sequence) в БД.
     *
     * @param worker объект Worker (без id — id присваивается БД)
     * @param owner  логин пользователя, создавшего объект
     * @return ID вставленного объекта, или -1 при ошибке
     */
    public long insertWorker(Worker worker, String owner) {
        long orgId = insertOrganization(worker.getOrganization());
        if (orgId < 0) return -1;

        String sql = """
                INSERT INTO s505045_workers
                    (name, coordinates_x, coordinates_y, creation_date, salary, position, status, organization_id, owner)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, worker.getName());
            ps.setInt(2, worker.getCoordinates().getX());
            ps.setDouble(3, worker.getCoordinates().getY());
            ps.setTimestamp(4, new Timestamp(worker.getCreationDate().getTime()));
            if (worker.getSalary() != null) {
                ps.setInt(5, worker.getSalary());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.setString(6, worker.getPosition().name());
            if (worker.getStatus() != null) {
                ps.setString(7, worker.getStatus().name());
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            ps.setLong(8, orgId);
            ps.setString(9, owner);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long id = generatedKeys.getLong(1);
                        logger.info("Worker вставлен в БД: ID=" + id + ", owner=" + owner);
                        return id;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка вставки Worker в БД: " + e.getMessage(), e);
        }
        return -1;
    }

    /**
     * Обновляет существующий Worker в БД.
     * Проверяет, что текущий пользователь является владельцем объекта.
     *
     * @param id      ID обновляемого Worker
     * @param worker  новые данные Worker
     * @param owner   логин пользователя, выполняющего операцию
     * @return true, если обновление прошло успешно
     */
    public boolean updateWorker(long id, Worker worker, String owner) {
        // Сначала обновляем организацию
        long orgId = insertOrganization(worker.getOrganization());
        if (orgId < 0) return false;

        String sql = """
                UPDATE s505045_workers
                SET name=?, coordinates_x=?, coordinates_y=?, salary=?,
                    position=?, status=?, organization_id=?
                WHERE id=? AND owner=?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, worker.getName());
            ps.setInt(2, worker.getCoordinates().getX());
            ps.setDouble(3, worker.getCoordinates().getY());
            if (worker.getSalary() != null) {
                ps.setInt(4, worker.getSalary());
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.setString(5, worker.getPosition().name());
            if (worker.getStatus() != null) {
                ps.setString(6, worker.getStatus().name());
            } else {
                ps.setNull(6, Types.VARCHAR);
            }
            ps.setLong(7, orgId);
            ps.setLong(8, id);
            ps.setString(9, owner);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                logger.info("Worker обновлён в БД: ID=" + id + ", owner=" + owner);
                return true;
            } else {
                logger.warn("Обновление не выполнено: Worker ID=" + id + " не найден или не принадлежит " + owner);
                return false;
            }
        } catch (SQLException e) {
            logger.error("Ошибка обновления Worker в БД: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Удаляет Worker из БД по ID.
     * Проверяет, что текущий пользователь является владельцем объекта.
     *
     * @param id    ID удаляемого Worker
     * @param owner логин пользователя
     * @return true, если удаление прошло успешно
     */
    public boolean deleteWorker(long id, String owner) {
        // Сначала получим organization_id, чтобы потом удалить organization
        long orgId = getOrganizationIdByWorker(id);

        String sql = "DELETE FROM s505045_workers WHERE id=? AND owner=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, owner);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                logger.info("Worker удалён из БД: ID=" + id + ", owner=" + owner);
                if (orgId > 0) {
                    deleteOrganizationIfUnused(orgId);
                }
                return true;
            } else {
                logger.warn("Удаление не выполнено: Worker ID=" + id + " не найден или не принадлежит " + owner);
                return false;
            }
        } catch (SQLException e) {
            logger.error("Ошибка удаления Worker из БД: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Удаляет все Worker-ы, принадлежащие данному пользователю.
     *
     * @param owner логин пользователя
     * @return количество удалённых объектов
     */
    public int deleteAllWorkersByOwner(String owner) {
        String sql = "DELETE FROM s505045_workers WHERE owner=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, owner);
            int rows = ps.executeUpdate();
            logger.info("Удалено Worker-ов пользователя " + owner + ": " + rows);
            return rows;
        } catch (SQLException e) {
            logger.error("Ошибка очистки Worker-ов пользователя " + owner + ": " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Проверяет владельца Worker-а.
     *
     * @param workerId ID Worker-а
     * @param username логин проверяемого пользователя
     * @return true, если Worker принадлежит пользователю
     */
    public boolean isOwner(long workerId, String username) {
        String sql = "SELECT owner FROM s505045_workers WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, workerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return username.equals(rs.getString("owner"));
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка проверки владельца: " + e.getMessage(), e);
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Вспомогательные методы для Organization
    // -------------------------------------------------------------------------

    private long insertOrganization(Organization org) {
        String sql = "INSERT INTO s505045_organizations (employees_count, org_type) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (org.getEmployeesCount() != null) {
                ps.setInt(1, org.getEmployeesCount());
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, org.getType().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        } catch (SQLException e) {
            logger.error("Ошибка вставки Organization: " + e.getMessage(), e);
        }
        return -1;
    }

    private long getOrganizationIdByWorker(long workerId) {
        String sql = "SELECT organization_id FROM s505045_workers WHERE id=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, workerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("organization_id");
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения organization_id: " + e.getMessage(), e);
        }
        return -1;
    }

    private void deleteOrganizationIfUnused(long orgId) {
        // Удаляем организацию только если нет больше ссылок на неё
        String checkSql = "SELECT COUNT(*) FROM s505045_workers WHERE organization_id=?";
        try (PreparedStatement ps = connection.prepareStatement(checkSql)) {
            ps.setLong(1, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (PreparedStatement delPs = connection.prepareStatement(
                            "DELETE FROM s505045_organizations WHERE id=?")) {
                        delPs.setLong(1, orgId);
                        delPs.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка удаления Organization: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Маппинг ResultSet → Worker
    // -------------------------------------------------------------------------

    private Worker mapResultSetToWorker(ResultSet rs) throws SQLException {
        Worker worker = new Worker();
        worker.setId(rs.getLong("id"));
        worker.setName(rs.getString("name"));

        Coordinates coords = new Coordinates(
                rs.getInt("coordinates_x"),
                rs.getDouble("coordinates_y")
        );
        worker.setCoordinates(coords);

        Timestamp ts = rs.getTimestamp("creation_date");
        worker.setCreationDate(new java.util.Date(ts.getTime()));

        int salary = rs.getInt("salary");
        worker.setSalary(rs.wasNull() ? null : salary);

        worker.setPosition(Position.valueOf(rs.getString("position")));

        String statusStr = rs.getString("status");
        worker.setStatus(statusStr != null ? Status.valueOf(statusStr) : null);

        Organization org = new Organization();
        int empCount = rs.getInt("employees_count");
        org.setEmployeesCount(rs.wasNull() ? null : empCount);
        org.setType(OrganizationType.valueOf(rs.getString("org_type")));
        worker.setOrganization(org);

        // Сохраняем owner (имя пользователя) в Worker через отдельное поле
        // (Worker не имеет поля owner — читаем и сохраняем через ThreadLocal или передаём отдельно)
        // Тут просто возвращаем Worker; owner учитывается в WorkerManager
        return worker;
    }

    // -------------------------------------------------------------------------
    // Авторизация и регистрация пользователей
    // -------------------------------------------------------------------------

    /**
     * Регистрирует нового пользователя в БД.
     * Пароль хэшируется алгоритмом SHA-1.
     *
     * @param username логин нового пользователя
     * @param password пароль в открытом виде
     * @return true, если регистрация прошла успешно (логин не занят)
     */
    public boolean registerUser(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        String hash = sha1(password);
        if (hash == null) return false;

        String sql = "INSERT INTO s505045_users (username, password_sha1) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.executeUpdate();
            logger.info("Зарегистрирован новый пользователь: " + username);
            return true;
        } catch (SQLException e) {
            // Код 23505 = нарушение уникальности (логин уже занят)
            if ("23505".equals(e.getSQLState())) {
                logger.warn("Попытка регистрации с занятым логином: " + username);
            } else {
                logger.error("Ошибка регистрации пользователя: " + e.getMessage(), e);
            }
            return false;
        }
    }

    /**
     * Проверяет логин и пароль пользователя.
     * Пароль хэшируется SHA-1 и сравнивается с хранимым хешем.
     *
     * @param username логин
     * @param password пароль в открытом виде
     * @return true, если авторизация успешна
     */
    public boolean authenticateUser(String username, String password) {
        if (username == null || password == null) return false;
        String hash = sha1(password);
        if (hash == null) return false;

        String sql = "SELECT 1 FROM s505045_users WHERE username=? AND password_sha1=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Ошибка авторизации: " + e.getMessage(), e);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Хэширование SHA-1
    // -------------------------------------------------------------------------

    /**
     * Хэширует строку алгоритмом SHA-1 и возвращает hex-строку (40 символов).
     *
     * @param input строка для хэширования
     * @return SHA-1 хэш в виде hex, или null при ошибке
     */
    public static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 гарантированно доступен в JVM, этот блок никогда не выполнится
            throw new RuntimeException("SHA-1 недоступен", e);
        }
    }

    // -------------------------------------------------------------------------
    // Закрытие соединения
    // -------------------------------------------------------------------------

    /**
     * Закрывает соединение с БД. Вызывается при завершении работы сервера.
     */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Соединение с PostgreSQL закрыто.");
            }
        } catch (SQLException e) {
            logger.error("Ошибка закрытия соединения: " + e.getMessage(), e);
        }
    }
}
