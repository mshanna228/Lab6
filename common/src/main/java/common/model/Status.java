package common.model;

import java.io.Serializable;

/**
 * Перечисление статусов работника.
 */
public enum Status implements Serializable {
    FIRED,
    HIRED,
    RECOMMENDED_FOR_PROMOTION,
    REGULAR,
    PROBATION;
}
