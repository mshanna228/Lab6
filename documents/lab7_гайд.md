# Лабораторная работа №7 — Гайд

> **Вариант 56.** Хранение коллекции в PostgreSQL, авторизация пользователей, многопоточность.

---

## Содержание

1. [Теория](#теория)
   - [JDBC — взаимодействие с базой данных](#jdbc)
   - [Многопоточность в Java](#многопоточность)
   - [Пулы потоков (ThreadPool)](#пулы-потоков)
   - [ReadWriteLock — синхронизация коллекции](#readwritelock)
   - [Хэширование SHA-1](#хэширование-sha-1)
2. [Архитектура программы](#архитектура-программы)
3. [Описание файлов](#описание-файлов)
4. [Схема базы данных](#схема-базы-данных)
5. [Как запустить программу](#как-запустить-программу)
6. [Как работает программа](#как-работает-программа)
7. [Примеры команд](#примеры-команд)

---

## Теория

### JDBC

**JDBC (Java Database Connectivity)** — стандартный API Java для взаимодействия с реляционными базами данных.

#### Порядок взаимодействия с базой данных

```
Приложение → DriverManager → Connection → Statement/PreparedStatement → ResultSet
```

1. **Загрузка драйвера**: `Class.forName("org.postgresql.Driver")` — регистрирует PostgreSQL-драйвер в JVM.

2. **Получение соединения**:
   ```java
   Connection conn = DriverManager.getConnection(
       "jdbc:postgresql://хост/база_данных",
       "пользователь",
       "пароль"
   );
   ```
   `Connection` — физическое соединение с БД. Создание соединения — дорогая операция, поэтому оно используется как синглтон.

3. **Создание запроса**:
   - `Statement` — простой запрос (например, `CREATE TABLE`):
     ```java
     Statement stmt = conn.createStatement();
     stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ...");
     ```
   - `PreparedStatement` — параметризованный запрос (защита от SQL-инъекций):
     ```java
     PreparedStatement ps = conn.prepareStatement(
         "INSERT INTO workers(name, salary) VALUES (?, ?)"
     );
     ps.setString(1, "Иванов");
     ps.setInt(2, 50000);
     ps.executeUpdate();
     ```

4. **Получение результата** через `ResultSet`:
   ```java
   ResultSet rs = ps.executeQuery(); // SELECT-запрос
   while (rs.next()) {
       String name = rs.getString("name");
       int salary = rs.getInt("salary");
   }
   ```

5. **Получение сгенерированных ключей** (SERIAL/sequence):
   ```java
   PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
   ps.executeUpdate();
   ResultSet keys = ps.getGeneratedKeys();
   if (keys.next()) {
       long newId = keys.getLong(1); // ID, сгенерированный SERIAL
   }
   ```

#### Классы и интерфейсы JDBC

| Интерфейс/Класс | Назначение |
|---|---|
| `DriverManager` | Создаёт соединения (`getConnection()`) |
| `Connection` | Физическое соединение с БД |
| `Statement` | Выполняет статические SQL-запросы |
| `PreparedStatement` | Параметризованные запросы (более безопасно и быстро) |
| `ResultSet` | Результат SELECT-запроса (курсор по строкам) |
| `Types` | Константы SQL-типов (`Types.INTEGER`, `Types.VARCHAR`) |

---

### Многопоточность

**Поток (Thread)** — наименьшая единица выполнения в Java. Потоки разделяют общую память процесса, что создаёт проблему гонки данных (race condition).

#### Thread и Runnable

```java
// Способ 1: наследование от Thread
Thread t = new Thread() {
    public void run() { /* код */ }
};
t.start();

// Способ 2: реализация Runnable (предпочтительный)
Runnable task = () -> { /* код */ };
Thread t = new Thread(task);
t.start();
```

#### Модификатор synchronized

```java
// Только один поток может выполнять этот метод одновременно
public synchronized String show() {
    return collection.toString();
}

// Синхронизация по объекту
synchronized (this) {
    // критическая секция
}
```

**Проблема `synchronized`**: при большом количестве потоков создаёт узкое место. Для оптимизации используется `ReadWriteLock`.

#### wait(), notify(), Lock, Condition

```java
// Устаревший подход (Object.wait/notify)
synchronized (lock) {
    while (!condition) lock.wait();
    // ... работа ...
    lock.notifyAll();
}

// Современный подход (Lock + Condition)
Lock lock = new ReentrantLock();
Condition ready = lock.newCondition();
lock.lock();
try {
    while (!condition) ready.await();
    // ... работа ...
    ready.signalAll();
} finally {
    lock.unlock();
}
```

#### volatile и атомарные типы

- `volatile` — гарантирует видимость изменений переменной всем потокам (но не атомарность):
  ```java
  private volatile boolean running = true; // в UDPServer
  ```
- `AtomicInteger`, `AtomicLong` — атомарные операции без синхронизации:
  ```java
  AtomicLong counter = new AtomicLong(0);
  long id = counter.incrementAndGet(); // thread-safe
  ```

---

### Пулы потоков

Создавать и удалять потоки вручную дорого. **Пул потоков** переиспользует потоки для выполнения задач.

#### ExecutorService и Executors

```java
// Fixed thread pool — фиксированное количество потоков
ExecutorService pool = Executors.newFixedThreadPool(4);
pool.submit(() -> { /* задача */ });

// Callable — задача с результатом
Future<String> future = pool.submit(() -> "результат");
String result = future.get(); // ожидание результата
```

**В нашем сервере:**
```java
// Для чтения запросов — 4+ потоков (по числу ядер CPU)
ExecutorService readPool = Executors.newFixedThreadPool(
    Math.max(4, Runtime.getRuntime().availableProcessors())
);
```

#### ForkJoinPool

`ForkJoinPool` — специальный пул для параллельного разделения задач (fork = разделить, join = собрать результат). Использует алгоритм **work stealing**: незанятые потоки "крадут" задачи у занятых.

```java
ForkJoinPool pool = ForkJoinPool.commonPool(); // общий пул JVM

// Запуск задачи
pool.submit(() -> {
    Response response = handleRequest(request);
    sendResponse(response, clientAddr, clientPort);
});
```

**В нашем сервере:**
- `FixedThreadPool` — читает входящие UDP-пакеты (I/O bound)
- `ForkJoinPool` — обрабатывает запрос и отправляет ответ (CPU bound, подходит для FJP)

#### Классы-синхронизаторы из java.util.concurrent

| Класс | Назначение |
|---|---|
| `CountDownLatch` | Ожидание завершения N операций |
| `CyclicBarrier` | Синхронизация группы потоков на барьере |
| `Semaphore` | Ограничение количества одновременных доступов |
| `BlockingQueue` | Потокобезопасная очередь с блокировкой |
| `ConcurrentHashMap` | Потокобезопасная HashMap |

---

### ReadWriteLock

`ReadWriteLock` — оптимизация для сценария "много читателей, мало писателей":
- **readLock**: несколько потоков могут читать **одновременно**
- **writeLock**: только один поток пишет, и никто не читает

```java
ReadWriteLock lock = new ReentrantReadWriteLock();

// Чтение — разрешено нескольким потокам одновременно
lock.readLock().lock();
try {
    return collection.stream().map(Worker::toString)...;
} finally {
    lock.readLock().unlock(); // всегда в finally!
}

// Запись — блокирует всех
lock.writeLock().lock();
try {
    collection.add(worker);
} finally {
    lock.writeLock().unlock();
}
```

**В нашей программе:**
- `readLock` → `show()`, `info()`, `head()`, `averageOfSalary()`, `countByStatus()`, `filterBySalary()`
- `writeLock` → `add()`, `update()`, `removeById()`, `clear()`

---

### Хэширование SHA-1

**SHA-1 (Secure Hash Algorithm 1)** — криптографическая хэш-функция, возвращающая 160-битный (40 символов hex) хэш строки. Пароли никогда не хранятся в открытом виде.

```java
MessageDigest md = MessageDigest.getInstance("SHA-1");
byte[] hashBytes = md.digest("мойпароль".getBytes(StandardCharsets.UTF_8));

// Конвертация байт в hex-строку
StringBuilder sb = new StringBuilder();
for (byte b : hashBytes) {
    sb.append(String.format("%02x", b));
}
String sha1 = sb.toString(); // например: "5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8"
```

---

## Архитектура программы

```
┌──────────────────────────────────────────────────────────────┐
│                        CLIENT                                │
│  ClientApp (авторизация) → UDPClient → [UDP пакет]          │
│  Request { command, arg, obj, login, password }              │
└───────────────────────────┬──────────────────────────────────┘
                            │ UDP (порт 1821)
┌───────────────────────────▼──────────────────────────────────┐
│                        SERVER                                │
│                                                              │
│  [UDP пакет]                                                 │
│      ↓ главный поток принимает пакет                         │
│  FixedThreadPool (чтение/десериализация)                     │
│      ↓ readPool.submit(задача)                               │
│  ForkJoinPool (обработка запроса)                            │
│      ↓ forkJoinPool.submit(задача)                           │
│  WorkerManager (ReadWriteLock)                               │
│      ↓ readLock / writeLock                                  │
│  PriorityQueue<Worker> (коллекция в памяти)                  │
│      ↓ при записи                                            │
│  DatabaseManager → PostgreSQL                                │
│      ↓ SERIAL генерирует ID                                  │
│  ForkJoinPool (отправка ответа)                              │
│      ↓                                                       │
│  [UDP ответ] → CLIENT                                        │
└──────────────────────────────────────────────────────────────┘
```

---

## Описание файлов

### Серверная часть (`server/`)

| Файл | Описание |
|---|---|
| `ServerApp.java` | Точка входа сервера. Создаёт `WorkerManager` и `UDPServer`, запускает сервер. |
| `DatabaseManager.java` | Синглтон. Подключение к PostgreSQL, создание таблиц, CRUD операции, регистрация/авторизация пользователей, SHA-1. |
| `LESHA_1.java` | Исходный файл студента — делегирует к `DatabaseManager`. |
| `WorkerManager.java` | Управляет коллекцией `PriorityQueue<Worker>` в памяти. Синхронизация через `ReadWriteLock`. Все изменения сначала пишет в БД, потом в память. |
| `UDPServer.java` | Принимает UDP-пакеты, десериализует запросы, проверяет авторизацию, выполняет команды. Многопоточность: `FixedThreadPool` + `ForkJoinPool`. |
| `FileManager.java` | Остаётся в проекте, но больше не используется (хранение заменено БД). |
| `IdManager.java` | Остаётся в проекте, но ID теперь генерируется SERIAL в PostgreSQL. |

### Клиентская часть (`client/`)

| Файл | Описание |
|---|---|
| `ClientApp.java` | Точка входа клиента. Авторизация при старте, добавляет login/password в каждый запрос. |
| `UDPClient.java` | Отправляет сериализованные запросы и получает ответы по UDP. |
| `WorkerReader.java` | Интерактивный ввод данных Worker с консоли. |
| `ConsoleInputManager.java` | Обёртка над Scanner для ввода с консоли. |
| `ScriptReader.java` | Чтение команд из файла скрипта. |

### Общие классы (`common/`)

| Файл | Описание |
|---|---|
| `Request.java` | Запрос клиента. Поля: `commandName`, `commandStringArgument`, `commandObjectArgument`, **`login`**, **`password`** (новые в ЛР7). |
| `Response.java` | Ответ сервера: `ResponseCode` (OK/ERROR) + `responseBody` (текст). |
| `Worker.java` | Элемент коллекции. Добавлено поле **`owner`** (логин создателя). |
| `Coordinates.java` | Координаты работника (x ≤ 709, y > -414). |
| `Organization.java` | Организация работника. |
| `Position.java` | Enum: `LABORER`, `LEAD_DEVELOPER`, `CLEANER`. |
| `Status.java` | Enum: `FIRED`, `HIRED`, `RECOMMENDED_FOR_PROMOTION`, `REGULAR`, `PROBATION`. |
| `OrganizationType.java` | Enum: `COMMERCIAL`, `GOVERNMENT`, `TRUST`, `PRIVATE_LIMITED_COMPANY`, `OPEN_JOINT_STOCK_COMPANY`. |

---

## Схема базы данных

```sql
-- Таблица пользователей
CREATE TABLE s505045_users (
    id            SERIAL PRIMARY KEY,
    username      VARCHAR(100) UNIQUE NOT NULL,
    password_sha1 CHAR(40) NOT NULL        -- SHA-1 хэш пароля (40 hex символов)
);

-- Таблица организаций (выделена в отдельную таблицу)
CREATE TABLE s505045_organizations (
    id              SERIAL PRIMARY KEY,
    employees_count INTEGER,               -- может быть NULL
    org_type        VARCHAR(50) NOT NULL   -- значение из OrganizationType enum
);

-- Таблица работников (главная таблица)
CREATE TABLE s505045_workers (
    id              SERIAL PRIMARY KEY,    -- ID генерируется автоматически (sequence)
    name            VARCHAR(100) NOT NULL,
    coordinates_x   INTEGER NOT NULL,
    coordinates_y   DOUBLE PRECISION NOT NULL,
    creation_date   TIMESTAMP NOT NULL,
    salary          INTEGER,               -- может быть NULL
    position        VARCHAR(50) NOT NULL,  -- значение из Position enum
    status          VARCHAR(50),           -- может быть NULL
    organization_id INTEGER REFERENCES s505045_organizations(id) ON DELETE SET NULL,
    owner           VARCHAR(100) NOT NULL REFERENCES s505045_users(username) ON DELETE CASCADE
);
```

**Отношения:**
- `workers.organization_id` → `organizations.id` (один к одному в данной реализации)
- `workers.owner` → `users.username` (один пользователь — много workers)

---

## Как запустить программу

### Требования

- Java 17+
- PostgreSQL (кафедральный сервер `pg`, база `studs`)
- Доступ по SSH к серверу кафедры (для запуска сервера)

### 1. Сборка проекта

```powershell
# В корне проекта (C:\git\javaWorks\Lab6)
.\gradlew.bat build
```

### 2. Запуск сервера

```bash
# На кафедральном сервере (через SSH):
java -jar server/build/libs/server.jar

# Или через Gradle (локально для тестирования):
.\gradlew.bat :server:run
```

> **Примечание:** При первом запуске сервер автоматически создаёт таблицы в PostgreSQL. Повторный запуск безопасен — используется `CREATE TABLE IF NOT EXISTS`.

### 3. Запуск клиента

```powershell
# Подключение к серверу на localhost:1821 (по умолчанию)
.\gradlew.bat :client:run

# Или подключение к кафедральному серверу:
java -jar client/build/libs/client.jar <адрес_сервера> 1821
```

### 4. Авторизация

При запуске клиент сразу запрашивает авторизацию:

```
=========================================
  Менеджер коллекции рабочих (ЛР7)
=========================================
Вы не авторизованы. Выберите действие:
  register <логин> <пароль>  — регистрация
  login <логин> <пароль>     — вход
=========================================
> register alice secret123
Регистрация успешна. Добро пожаловать, alice!

alice@worker> help
```

---

## Как работает программа

### Поток данных: команда `add`

```
1. Пользователь вводит: add
2. ClientApp запрашивает данные Worker
3. ClientApp создаёт Request { command="add", obj=Worker, login="alice", password="secret123" }
4. UDPClient сериализует Request → байты
5. Байты отправляются UDP-пакетом на сервер :1821

--- На сервере ---

6. Главный поток socket.receive(packet) → получает пакет
7. readPool.submit(задача) — передаёт задачу в FixedThreadPool

--- В потоке FixedThreadPool ---

8. Десериализация Request
9. forkJoinPool.submit(задача) — передаёт обработку в ForkJoinPool

--- В потоке ForkJoinPool ---

10. Проверка авторизации: DatabaseManager.authenticateUser("alice", "secret123")
11. Команда "add" → WorkerManager.add(worker, "alice")

--- В WorkerManager ---

12. worker.setCreationDate(new Date())   // дата создания
13. worker.setOwner("alice")             // сохраняем владельца
14. DatabaseManager.insertWorker(worker, "alice")
    → INSERT INTO s505045_workers(...) RETURNING id
    → БД генерирует id = 42 (SERIAL)
15. Только при успехе: writeLock.lock() → collection.add(worker) → writeLock.unlock()
16. Возврат: "Рабочий успешно добавлен. ID: 42"

--- В ForkJoinPool (отправка) ---

17. forkJoinPool.submit(отправка)
18. Response { OK, "Рабочий успешно добавлен. ID: 42" } сериализуется → байты
19. UDP-пакет отправляется клиенту

--- На клиенте ---

20. UDPClient.sendAndReceive() получает Response
21. ClientApp выводит: "Рабочий успешно добавлен. ID: 42"
```

### Контроль доступа

- **Просмотр** (`show`, `info`, `head`, `average_of_salary`, `count_by_status`, `filter_by_salary`):  
  Доступен всем авторизованным пользователям. Видны все объекты коллекции.

- **Изменение** (`update`, `remove_by_id`, `clear`):  
  Только для своих объектов. Проверяется `worker.getOwner().equals(login)`.  
  При попытке изменить чужой объект: `"Ошибка: вы не можете изменить рабочего с ID X — он принадлежит другому пользователю."`

### ReadWriteLock в действии

```
Поток A: show()       → readLock.lock()   ✓ (читает)
Поток B: show()       → readLock.lock()   ✓ (читает одновременно с A)
Поток C: add(worker)  → writeLock.lock()  ⏳ (ждёт, пока A и B освободят readLock)

Когда A и B завершают чтение:
Поток C: add(worker)  → writeLock.lock()  ✓ (теперь пишет)
Поток D: show()       → readLock.lock()   ⏳ (ждёт C)
```

---

## Примеры команд

```bash
# Регистрация нового пользователя
> register alice mypassword

# Вход
> login alice mypassword

# Показать все элементы (все пользователи видят)
alice@worker> show

# Добавить нового работника
alice@worker> add
  Введите имя: Иванов Иван
  Введите координату X (≤709): 100
  Введите координату Y (>-414): 50.5
  Введите зарплату (или пусто): 75000
  Введите должность (LABORER/LEAD_DEVELOPER/CLEANER): LABORER
  Введите статус (FIRED/HIRED/RECOMMENDED_FOR_PROMOTION/REGULAR/PROBATION, или пусто): HIRED
  Введите кол-во сотрудников в организации (или пусто): 100
  Введите тип организации (COMMERCIAL/GOVERNMENT/TRUST/PRIVATE_LIMITED_COMPANY/OPEN_JOINT_STOCK_COMPANY): COMMERCIAL
Рабочий успешно добавлен. ID: 1

# Обновить свой объект
alice@worker> update 1
  ... (ввод новых данных)
Рабочий с ID 1 успешно обновлён.

# Удалить свой объект
alice@worker> remove_by_id 1
Рабочий с ID 1 успешно удалён.

# Попытка удалить чужой объект (если bob добавил worker с ID 5)
alice@worker> remove_by_id 5
Ошибка: вы не можете удалить рабочего с ID 5 — он принадлежит другому пользователю.

# Статистика
alice@worker> average_of_salary
Средняя зарплата рабочих в коллекции: 62500.0

alice@worker> count_by_status HIRED
Количество рабочих со статусом HIRED: 3

alice@worker> filter_by_salary 75000
Worker{id=1, name='Иванов Иван', ...}

# Информация о коллекции
alice@worker> info
Информация о коллекции:
  Тип: java.util.PriorityQueue
  Дата инициализации: 2026-06-13T11:00:00
  Количество элементов: 5

# Выполнить скрипт
alice@worker> execute_script commands.txt

# Завершить работу клиента
alice@worker> exit
```
