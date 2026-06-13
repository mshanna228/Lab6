package common.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Класс Worker — основной элемент коллекции.
 * Реализует Serializable для передачи по сети и Comparable для сортировки.
 */
public class Worker implements Serializable, Comparable<Worker> {
    private static final long serialVersionUID = 1L;

    private long id;                    // > 0, уникальный, генерируется автоматически
    private String name;                // не null, не пустая строка
    private Coordinates coordinates;    // не null
    private java.util.Date creationDate; // не null, генерируется автоматически
    private Integer salary;             // может быть null, > 0
    private Position position;          // не null
    private Status status;              // может быть null
    private Organization organization;  // не null
    private String owner;               // логин пользователя, создавшего объект (заполняется сервером)

    /**
     * Конструктор для создания нового Worker (без id и creationDate — они генерируются на сервере).
     */
    public Worker(String name, Coordinates coordinates, Integer salary,
                  Position position, Status status, Organization organization) {
        this.name = name;
        this.coordinates = coordinates;
        this.salary = salary;
        this.position = position;
        this.status = status;
        this.organization = organization;
    }

    public Worker() {}

    public long getId() {
        return id;
    }

    public void setId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID должен быть больше 0");
        }
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Имя не может быть пустым или null");
        }
        this.name = name;
    }

    public Coordinates getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Coordinates coordinates) {
        if (coordinates == null) {
            throw new IllegalArgumentException("Coordinates не могут быть null");
        }
        this.coordinates = coordinates;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        if (creationDate == null) {
            throw new IllegalArgumentException("creationDate не может быть null");
        }
        this.creationDate = creationDate;
    }

    public void setSalary(Integer salary) {
        if (salary != null && salary <= 0) {
            throw new IllegalArgumentException("Salary должно быть больше 0");
        }
        this.salary = salary;
    }

    public Integer getSalary() {
        return salary;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }

    public void setPosition(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("Position не может быть null");
        }
        this.position = position;
    }

    public Position getPosition() {
        return position;
    }

    public void setOrganization(Organization organization) {
        if (organization == null) {
            throw new IllegalArgumentException("Organization не может быть null");
        }
        this.organization = organization;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * Сравнение по местоположению (Coordinates) — требование задания.
     */
    @Override
    public int compareTo(Worker other) {
        return this.coordinates.compareTo(other.coordinates);
    }

    @Override
    public String toString() {
        return "Worker{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", coordinates=" + coordinates +
                ", creationDate=" + creationDate +
                ", salary=" + salary +
                ", position=" + position +
                ", status=" + status +
                ", organization=" + organization +
                ", owner='" + owner + '\''+
                '}';
    }
}
