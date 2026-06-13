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
    private String login;    // логин пользователя
    private String password; // пароль в открытом виде

    public Request(String commandName, String commandStringArgument, Serializable commandObjectArgument) {
        this.commandName = commandName;
        this.commandStringArgument = commandStringArgument;
        this.commandObjectArgument = commandObjectArgument;
    }

    public Request(String commandName, String commandStringArgument, Serializable commandObjectArgument,
                   String login, String password) {
        this(commandName, commandStringArgument, commandObjectArgument);
        this.login = login;
        this.password = password;
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

    public String getLogin() {
        return login != null ? login : "";
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password != null ? password : "";
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEmpty() {
        return commandName.isEmpty() && commandStringArgument.isEmpty() && commandObjectArgument == null;
    }

    @Override
    public String toString() {
        return "Request[" + commandName + ", " + commandStringArgument + ", " + commandObjectArgument + "]";
    }
}
