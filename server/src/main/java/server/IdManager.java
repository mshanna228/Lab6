package server;

import java.util.HashSet;
import java.util.Set;

/**
 * Менеджер для генерации и контроля уникальности ID на сервере.
 */
public class IdManager {
    private static long nextId = 1;
    private static final Set<Long> usedIds = new HashSet<>();

    /**
     * Генерирует новый уникальный ID.
     * @return новый ID.
     */
    public static synchronized long generateId() {
        while (usedIds.contains(nextId)) {
            nextId++;
        }
        usedIds.add(nextId);
        return nextId++;
    }

    /**
     * Проверяет, занят ли ID.
     * @param id ID для проверки.
     * @return true, если занят.
     */
    public static synchronized boolean isIdUsed(long id) {
        return usedIds.contains(id);
    }

    /**
     * Добавляет ID в список использованных.
     * @param id ID для добавления.
     */
    public static synchronized void useId(long id) {
        usedIds.add(id);
        if (id >= nextId) {
            nextId = id + 1;
        }
    }

    /**
     * Удаляет ID из списка использованных.
     * @param id ID для удаления.
     */
    public static synchronized void removeId(long id) {
        usedIds.remove(id);
    }

    /**
     * Очищает занятые ID.
     */
    public static synchronized void clear() {
        usedIds.clear();
        nextId = 1;
    }
}
