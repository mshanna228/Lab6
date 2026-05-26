package common.model;

import java.io.Serializable;

/**
 * Класс организации.
 * employeesCount — может быть null, значение должно быть больше 0.
 * type — не может быть null.
 */
public class Organization implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer employeesCount; // Поле может быть null, Значение поля должно быть больше 0
    private OrganizationType type;  // Поле не может быть null

    public Organization(Integer employeesCount, OrganizationType type) {
        setEmployeesCount(employeesCount);
        setType(type);
    }

    public Organization() {}

    public void setType(OrganizationType type) {
        if (type == null) {
            throw new IllegalArgumentException("Тип организации не может быть null");
        }
        this.type = type;
    }

    public void setEmployeesCount(Integer employeesCount) {
        if (employeesCount != null && employeesCount <= 0) {
            throw new IllegalArgumentException("Количество сотрудников должно быть больше 0");
        }
        this.employeesCount = employeesCount;
    }

    public Integer getEmployeesCount() {
        return employeesCount;
    }

    public OrganizationType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Organization{employeesCount=" + employeesCount + ", type=" + type + "}";
    }
}
