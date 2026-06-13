package server;

/**
 * LESHA_1 — первоначальный файл, с которого началась работа над ЛР7.
 *
 * <p>Вся логика вынесена в {@link DatabaseManager}, который является синглтоном
 * и выполняет подключение к PostgreSQL, создание таблиц, CRUD и авторизацию.
 *
 * <p>Этот класс оставлен для истории и перенаправляет вызовы в DatabaseManager.
 *
 * @see DatabaseManager
 */
public class LESHA_1 {
    /**
     * Ссылка на синглтон DatabaseManager.
     * Через неё можно получить доступ ко всем операциям с БД.
     */
    public static final DatabaseManager INSTANCE = DatabaseManager.INSTANCE;
}