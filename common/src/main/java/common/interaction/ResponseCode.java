package common.interaction;

import java.io.Serializable;

/**
 * Перечисление кодов ответа сервера.
 */
public enum ResponseCode implements Serializable {
    /** Команда выполнена успешно */
    OK,
    /** Произошла ошибка при выполнении команды */
    ERROR,
    /** Сервер завершает работу */
    SERVER_EXIT
}
