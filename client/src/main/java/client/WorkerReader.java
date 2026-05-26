package client;

import common.model.*;
import java.util.Arrays;

/**
 * Класс для чтения объектов Worker с консоли или из скрипта с валидацией.
 */
public class WorkerReader {
    private final ConsoleInputManager inputManager;
    private final ScriptReader scriptReader;

    public WorkerReader(ConsoleInputManager inputManager, ScriptReader scriptReader) {
        this.inputManager = inputManager;
        this.scriptReader = scriptReader;
    }

    private String getNextLine(String message) {
        if (scriptReader != null && !scriptReader.isEmpty()) {
            String line = scriptReader.readLine();
            if (line != null) {
                System.out.println(message + " [считано из скрипта]: " + line);
            }
            return line;
        }
        return inputManager.readLine(message);
    }

    public Worker readWorker() {
        String name = readString("Введите имя рабочего:", false);
        Coordinates coordinates = readCoordinates();
        Integer salary = readInt("Введите зарплату:", true);
        Position position = readEnum("Введите должность (LABORER, LEAD_DEVELOPER, CLEANER):", Position.class, false);
        Status status = readEnum("Введите статус (FIRED, HIRED, RECOMMENDED_FOR_PROMOTION, REGULAR, PROBATION):",
                Status.class, true);
        Organization organization = readOrganization();

        return new Worker(name, coordinates, salary, position, status, organization);
    }

    public Coordinates readCoordinates() {
        Integer x;
        while (true) {
            x = readInt("Введите координату X (макс. 709):", false);
            if (x != null && x <= 709) break;
            System.out.println("Ошибка: X не может быть больше 709.");
        }

        Double y;
        while (true) {
            y = readDouble("Введите координату Y (больше -414):", false);
            if (y != null && y > -414) break;
            System.out.println("Ошибка: Y должен быть больше -414.");
        }

        return new Coordinates(x, y);
    }

    public Organization readOrganization() {
        OrganizationType type = readEnum(
                "Введите тип организации (COMMERCIAL, GOVERNMENT, TRUST, PRIVATE_LIMITED_COMPANY, OPEN_JOINT_STOCK_COMPANY):",
                OrganizationType.class, false);
        // employeesCount по ТЗ ЛР5/ЛР6 равен null или считывается. Посмотрим на конструктор Organization.
        // public Organization(Integer employeesCount, OrganizationType type)
        // В ТЗ ЛР5/6 employeesCount может быть null, значение > 0.
        Integer count = readInt("Введите количество сотрудников организации (или оставьте пустым):", true);
        while (count != null && count <= 0) {
            System.out.println("Ошибка: количество сотрудников должно быть больше 0.");
            count = readInt("Введите количество сотрудников организации (или оставьте пустым):", true);
        }
        return new Organization(count, type);
    }

    private String readString(String message, boolean canBeNull) {
        while (true) {
            String s = getNextLine(message);
            if (s == null || s.trim().isEmpty()) {
                if (canBeNull) return null;
                if (scriptReader != null && !scriptReader.isEmpty()) {
                    throw new RuntimeException("Ошибка в скрипте: пустое значение в обязательном поле.");
                }
                System.out.println("Ошибка: поле не может быть пустым.");
                continue;
            }
            return s;
        }
    }

    private Integer readInt(String message, boolean canBeNull) {
        while (true) {
            String s = getNextLine(message);
            if (s == null || s.trim().isEmpty()) {
                if (canBeNull) return null;
                System.out.println("Ошибка: поле не может быть пустым.");
                continue;
            }
            try {
                java.math.BigInteger bigVal = new java.math.BigInteger(s.trim());
                if (bigVal.compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE)) > 0 ||
                        bigVal.compareTo(java.math.BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                    System.out.println("Ошибка: число выходит за пределы допустимого диапазона Integer.");
                    continue;
                }
                return bigVal.intValue();
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: введите корректное целое число.");
            }
        }
    }

    private Double readDouble(String message, boolean canBeNull) {
        while (true) {
            String s = getNextLine(message);
            if (s == null || s.trim().isEmpty()) {
                if (canBeNull) return null;
                System.out.println("Ошибка: поле не может быть пустым.");
                continue;
            }
            try {
                return Double.parseDouble(s.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: введите число.");
            }
        }
    }

    private <E extends Enum<E>> E readEnum(String message, Class<E> enumClass, boolean canBeNull) {
        while (true) {
            String s = getNextLine(message);
            if (s == null || s.trim().isEmpty()) {
                if (canBeNull) return null;
                System.out.println("Ошибка: поле не может быть пустым.");
                continue;
            }
            try {
                return Enum.valueOf(enumClass, s.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                System.out.println("Ошибка: введите значение из списка: " + Arrays.toString(enumClass.getEnumConstants()));
            }
        }
    }
}
