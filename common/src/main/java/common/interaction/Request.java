package common.interaction;

import java.io.Serializable;

/**
 * Класс запроса от клиента к серверу.
 * Содержит имя команды, строковый аргумент и объектный аргумент.
 * Все команды передаются как объекты (не строки) — требование задания.
 */
public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    private String commandName;
    private String commandStringArgument;
    private Serializable commandObjectArgument;

    public Request(String commandName, String commandStringArgument, Serializable commandObjectArgument) {
        this.commandName = commandName;
        this.commandStringArgument = commandStringArgument;
        this.commandObjectArgument = commandObjectArgument;
    }

    public Request(String commandName, String commandStringArgument) {
        this(commandName, commandStringArgument, null);
    }

    public Request() {
        this("", "");
    }

    public String getCommandName() {
        return commandName;
    }

    public String getCommandStringArgument() {
        return commandStringArgument;
    }

    public Serializable getCommandObjectArgument() {
        return commandObjectArgument;
    }

    public boolean isEmpty() {
        return commandName.isEmpty() && commandStringArgument.isEmpty() && commandObjectArgument == null;
    }

    @Override
    public String toString() {
        return "Request[" + commandName + ", " + commandStringArgument + ", " + commandObjectArgument + "]";
    }
}
