package server;

import common.model.Worker;
import common.model.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

/**
 * Менеджер коллекции рабочих на сервере.
 * Все операции над объектами коллекции реализованы с помощью Stream API и лямбда-выражений.
 */
public class WorkerManager {
    private static final Logger logger = LoggerFactory.getLogger(WorkerManager.class);
    private final PriorityQueue<Worker> collection;
    private final LocalDateTime lastInitTime;
    private final FileManager fileManager;

    public WorkerManager(FileManager fileManager) {
        this.lastInitTime = LocalDateTime.now();
        this.fileManager = fileManager;
        this.collection = fileManager.readCollection();
    }

    public synchronized String info() {
        return "Информация о коллекции:\n" +
                "  Тип: " + collection.getClass().getName() + "\n" +
                "  Дата инициализации: " + lastInitTime + "\n" +
                "  Количество элементов: " + collection.size();
    }

    public synchronized String show() {
        if (collection.isEmpty()) {
            return "Коллекция пуста.";
        }
        // Объекты в коллекции, передаваемой клиенту, отсортированы по местоположению (Worker.compareTo)
        return collection.stream()
                .sorted()
                .map(Worker::toString)
                .collect(Collectors.joining("\n"));
    }

    public synchronized String add(Worker worker) {
        worker.setId(IdManager.generateId());
        worker.setCreationDate(new java.util.Date());
        collection.add(worker);
        IdManager.useId(worker.getId());
        logger.info("Добавлен новый рабочий: ID=" + worker.getId() + ", Name=" + worker.getName());
        return "Рабочий успешно добавлен. Сгенерирован ID: " + worker.getId();
    }

    public synchronized String update(long id, Worker worker) {
        Worker existing = collection.stream()
                .filter(w -> w.getId() == id)
                .findFirst()
                .orElse(null);
        if (existing == null) {
            logger.warn("Попытка обновить несуществующего рабочего: ID=" + id);
            return "Рабочий с ID " + id + " не найден.";
        }
        collection.remove(existing);
        worker.setId(id);
        worker.setCreationDate(existing.getCreationDate()); // Сохраняем дату создания
        collection.add(worker);
        logger.info("Обновлен рабочий: ID=" + id + ", Name=" + worker.getName());
        return "Рабочий с ID " + id + " успешно обновлен.";
    }

    public synchronized String removeById(long id) {
        Worker worker = collection.stream()
                .filter(w -> w.getId() == id)
                .findFirst()
                .orElse(null);
        if (worker != null) {
            collection.remove(worker);
            IdManager.removeId(id);
            logger.info("Удален рабочий: ID=" + id);
            return "Рабочий с ID " + id + " успешно удален.";
        } else {
            logger.warn("Попытка удалить несуществующего рабочего: ID=" + id);
            return "Рабочий с ID " + id + " не найден в коллекции.";
        }
    }

    public synchronized String clear() {
        int size = collection.size();
        collection.clear();
        IdManager.clear();
        logger.info("Коллекция очищена. Удалено элементов: " + size);
        return "Коллекция очищена. Удалено элементов: " + size;
    }

    public synchronized void save() {
        fileManager.saveCollection(collection);
    }

    public synchronized String head() {
        Worker peek = collection.peek();
        return peek == null ? "Коллекция пуста." : "Первый элемент коллекции: " + peek.toString();
    }

    public synchronized String addIfMax(Worker worker) {
        Worker max = collection.stream().max(Worker::compareTo).orElse(null);
        if (max == null || worker.compareTo(max) > 0) {
            worker.setId(IdManager.generateId());
            worker.setCreationDate(new java.util.Date());
            collection.add(worker);
            IdManager.useId(worker.getId());
            logger.info("Добавлен новый рабочий (add_if_max): ID=" + worker.getId() + ", Name=" + worker.getName());
            return "Рабочий успешно добавлен (его значение максимально). ID: " + worker.getId();
        }
        return "Рабочий не добавлен: его значение не является максимальным в коллекции.";
    }

    public synchronized String addIfMin(Worker worker) {
        Worker min = collection.stream().min(Worker::compareTo).orElse(null);
        if (min == null || worker.compareTo(min) < 0) {
            worker.setId(IdManager.generateId());
            worker.setCreationDate(new java.util.Date());
            collection.add(worker);
            IdManager.useId(worker.getId());
            logger.info("Добавлен новый рабочий (add_if_min): ID=" + worker.getId() + ", Name=" + worker.getName());
            return "Рабочий успешно добавлен (его значение минимально). ID: " + worker.getId();
        }
        return "Рабочий не добавлен: его значение не является минимальным в коллекции.";
    }

    public synchronized String averageOfSalary() {
        double avg = collection.stream()
                .filter(w -> w.getSalary() != null)
                .mapToInt(Worker::getSalary)
                .average()
                .orElse(0);
        return "Средняя зарплата рабочих в коллекции: " + avg;
    }

    public synchronized String countByStatus(Status status) {
        long count = collection.stream()
                .filter(w -> w.getStatus() == status)
                .count();
        return "Количество рабочих со статусом " + status + ": " + count;
    }

    public synchronized String filterBySalary(Integer salary) {
        String result = collection.stream()
                .filter(w -> (salary == null && w.getSalary() == null) || (salary != null && salary.equals(w.getSalary())))
                .sorted() // Сортируем при передаче
                .map(Worker::toString)
                .collect(Collectors.joining("\n"));
        return result.isEmpty() ? "Рабочие с зарплатой " + salary + " не найдены." : result;
    }

    public synchronized PriorityQueue<Worker> getCollection() {
        return collection;
    }
}
