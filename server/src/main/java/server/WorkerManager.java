package server;

import common.model.Worker;
import common.model.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * WorkerManager — менеджер коллекции рабочих на сервере.
 *
 * <p>Реализует требования ЛР7:</p>
 * <ul>
 *   <li>Коллекция в памяти синхронизируется через {@link ReadWriteLock}:
 *       читающие команды (show, info, head) берут readLock,
 *       изменяющие (add, update, remove, clear) берут writeLock.</li>
 *   <li>Состояние коллекции в памяти обновляется только после успешной
 *       записи в БД.</li>
 *   <li>Команды получения данных работают с коллекцией в памяти.</li>
 *   <li>Модифицирующие команды проверяют владельца объекта.</li>
 * </ul>
 *
 * <p>FileManager больше не используется — хранение в файле заменено PostgreSQL.</p>
 */
public class WorkerManager {
    private static final Logger logger = LoggerFactory.getLogger(WorkerManager.class);

    /** Коллекция в памяти. Синхронизируется через ReadWriteLock. */
    private final PriorityQueue<Worker> collection;

    /** Дата инициализации коллекции (при старте сервера). */
    private final LocalDateTime lastInitTime;

    /**
     * ReadWriteLock для синхронизации доступа к коллекции.
     * <ul>
     *   <li>readLock() — для команд чтения (show, info, head, average_of_salary и т.д.)</li>
     *   <li>writeLock() — для команд записи (add, update, remove_by_id, clear)</li>
     * </ul>
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** Ссылка на DatabaseManager для CRUD и авторизации. */
    private final DatabaseManager db = DatabaseManager.INSTANCE;

    public WorkerManager() {
        this.lastInitTime = LocalDateTime.now();
        // Загружаем коллекцию из БД один раз при старте
        this.collection = db.loadAllWorkers();
        logger.info("Коллекция загружена из БД. Элементов: " + collection.size());
    }

    // -------------------------------------------------------------------------
    // Команды чтения (используют readLock)
    // -------------------------------------------------------------------------

    /**
     * Возвращает информацию о коллекции.
     */
    public String info() {
        lock.readLock().lock();
        try {
            return "Информация о коллекции:\n" +
                    "  Тип: " + collection.getClass().getName() + "\n" +
                    "  Дата инициализации: " + lastInitTime + "\n" +
                    "  Количество элементов: " + collection.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Возвращает строковое представление всех элементов коллекции, отсортированных по координатам.
     */
    public String show() {
        lock.readLock().lock();
        try {
            if (collection.isEmpty()) {
                return "Коллекция пуста.";
            }
            return collection.stream()
                    .sorted()
                    .map(Worker::toString)
                    .collect(Collectors.joining("\n"));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Возвращает первый (минимальный по координатам) элемент коллекции.
     */
    public String head() {
        lock.readLock().lock();
        try {
            Worker peek = collection.peek();
            return peek == null ? "Коллекция пуста." : "Первый элемент коллекции: " + peek.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Возвращает среднюю зарплату всех Worker-ов в коллекции.
     */
    public String averageOfSalary() {
        lock.readLock().lock();
        try {
            double avg = collection.stream()
                    .filter(w -> w.getSalary() != null)
                    .mapToInt(Worker::getSalary)
                    .average()
                    .orElse(0);
            return "Средняя зарплата рабочих в коллекции: " + avg;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Возвращает количество Worker-ов с заданным статусом.
     */
    public String countByStatus(Status status) {
        lock.readLock().lock();
        try {
            long count = collection.stream()
                    .filter(w -> w.getStatus() == status)
                    .count();
            return "Количество рабочих со статусом " + status + ": " + count;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Возвращает Worker-ов с заданной зарплатой.
     */
    public String filterBySalary(Integer salary) {
        lock.readLock().lock();
        try {
            String result = collection.stream()
                    .filter(w -> (salary == null && w.getSalary() == null)
                            || (salary != null && salary.equals(w.getSalary())))
                    .sorted()
                    .map(Worker::toString)
                    .collect(Collectors.joining("\n"));
            return result.isEmpty() ? "Рабочие с зарплатой " + salary + " не найдены." : result;
        } finally {
            lock.readLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Команды записи (используют writeLock)
    // -------------------------------------------------------------------------

    /**
     * Добавляет нового Worker-а в коллекцию.
     * Сначала вставляет в БД (БД генерирует ID через SERIAL),
     * только при успехе добавляет в коллекцию в памяти.
     *
     * @param worker новый Worker (без ID)
     * @param owner  логин пользователя, создающего объект
     * @return строка с результатом
     */
    public String add(Worker worker, String owner) {
        worker.setCreationDate(new java.util.Date());
        worker.setOwner(owner);

        // Сначала пишем в БД — получаем ID от SERIAL sequence
        long newId = db.insertWorker(worker, owner);
        if (newId < 0) {
            return "Ошибка: не удалось добавить рабочего в базу данных.";
        }
        worker.setId(newId);

        // Только после успешной записи в БД обновляем коллекцию в памяти
        lock.writeLock().lock();
        try {
            collection.add(worker);
        } finally {
            lock.writeLock().unlock();
        }

        logger.info("Добавлен новый Worker: ID=" + newId + ", owner=" + owner);
        return "Рабочий успешно добавлен. ID: " + newId;
    }

    /**
     * Обновляет Worker с заданным ID.
     * Проверяет, что Worker принадлежит пользователю.
     *
     * @param id     ID обновляемого Worker
     * @param worker новые данные
     * @param owner  логин текущего пользователя
     * @return строка с результатом
     */
    public String update(long id, Worker worker, String owner) {
        // Проверяем существование и владельца в памяти
        lock.readLock().lock();
        Worker existing;
        try {
            existing = collection.stream()
                    .filter(w -> w.getId() == id)
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }

        if (existing == null) {
            return "Рабочий с ID " + id + " не найден.";
        }
        if (!owner.equals(existing.getOwner())) {
            return "Ошибка: вы не можете изменить рабочего с ID " + id + " — он принадлежит другому пользователю.";
        }

        // Обновляем в БД (также проверяется owner)
        worker.setCreationDate(existing.getCreationDate());
        boolean updated = db.updateWorker(id, worker, owner);
        if (!updated) {
            return "Ошибка: не удалось обновить рабочего в базе данных.";
        }

        // Обновляем в памяти
        lock.writeLock().lock();
        try {
            collection.remove(existing);
            worker.setId(id);
            worker.setCreationDate(existing.getCreationDate());
            worker.setOwner(owner);
            collection.add(worker);
        } finally {
            lock.writeLock().unlock();
        }

        logger.info("Обновлён Worker: ID=" + id + ", owner=" + owner);
        return "Рабочий с ID " + id + " успешно обновлён.";
    }

    /**
     * Удаляет Worker по ID.
     * Проверяет, что Worker принадлежит пользователю.
     *
     * @param id    ID удаляемого Worker
     * @param owner логин текущего пользователя
     * @return строка с результатом
     */
    public String removeById(long id, String owner) {
        // Проверяем в памяти
        lock.readLock().lock();
        Worker toRemove;
        try {
            toRemove = collection.stream()
                    .filter(w -> w.getId() == id)
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }

        if (toRemove == null) {
            return "Рабочий с ID " + id + " не найден в коллекции.";
        }
        if (!owner.equals(toRemove.getOwner())) {
            return "Ошибка: вы не можете удалить рабочего с ID " + id + " — он принадлежит другому пользователю.";
        }

        // Удаляем из БД
        boolean deleted = db.deleteWorker(id, owner);
        if (!deleted) {
            return "Ошибка: не удалось удалить рабочего из базы данных.";
        }

        // Удаляем из памяти
        lock.writeLock().lock();
        try {
            collection.remove(toRemove);
        } finally {
            lock.writeLock().unlock();
        }

        logger.info("Удалён Worker: ID=" + id + ", owner=" + owner);
        return "Рабочий с ID " + id + " успешно удалён.";
    }

    /**
     * Очищает коллекцию — удаляет только Worker-ов текущего пользователя.
     *
     * @param owner логин текущего пользователя
     * @return строка с результатом
     */
    public String clear(String owner) {
        // Удаляем из БД все Worker-ы данного пользователя
        int deletedFromDb = db.deleteAllWorkersByOwner(owner);

        // Удаляем из памяти
        lock.writeLock().lock();
        int removedFromMemory = 0;
        try {
            java.util.List<Worker> toRemove = collection.stream()
                    .filter(w -> owner.equals(w.getOwner()))
                    .collect(Collectors.toList());
            removedFromMemory = toRemove.size();
            collection.removeAll(toRemove);
        } finally {
            lock.writeLock().unlock();
        }

        logger.info("Очищено Worker-ов пользователя " + owner + ": " + removedFromMemory + " (в памяти), " + deletedFromDb + " (в БД)");
        return "Удалено ваших рабочих: " + removedFromMemory +
                ". Всего в коллекции осталось: " + collection.size();
    }

    /**
     * Добавляет Worker в коллекцию, если его значение превышает максимальное.
     *
     * @param worker новый Worker
     * @param owner  логин пользователя
     * @return строка с результатом
     */
    public String addIfMax(Worker worker, String owner) {
        lock.readLock().lock();
        Worker max;
        try {
            max = collection.stream().max(Worker::compareTo).orElse(null);
        } finally {
            lock.readLock().unlock();
        }

        if (max == null || worker.compareTo(max) > 0) {
            return add(worker, owner);
        }
        return "Рабочий не добавлен: его значение не является максимальным в коллекции.";
    }

    /**
     * Добавляет Worker в коллекцию, если его значение меньше минимального.
     *
     * @param worker новый Worker
     * @param owner  логин пользователя
     * @return строка с результатом
     */
    public String addIfMin(Worker worker, String owner) {
        lock.readLock().lock();
        Worker min;
        try {
            min = collection.stream().min(Worker::compareTo).orElse(null);
        } finally {
            lock.readLock().unlock();
        }

        if (min == null || worker.compareTo(min) < 0) {
            return add(worker, owner);
        }
        return "Рабочий не добавлен: его значение не является минимальным в коллекции.";
    }

    /**
     * Возвращает коллекцию (для тестов и отладки).
     */
    public PriorityQueue<Worker> getCollection() {
        lock.readLock().lock();
        try {
            return new PriorityQueue<>(collection);
        } finally {
            lock.readLock().unlock();
        }
    }
}
