package common.model;

import java.io.Serializable;

/**
 * Перечисление типов организации.
 */
public enum OrganizationType implements Serializable {
    COMMERCIAL,
    GOVERNMENT,
    TRUST,
    PRIVATE_LIMITED_COMPANY,
    OPEN_JOINT_STOCK_COMPANY;
}
